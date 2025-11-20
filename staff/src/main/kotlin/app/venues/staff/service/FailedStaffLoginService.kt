package app.venues.staff.service

import app.venues.common.constants.AppConstants
import app.venues.common.exception.VenuesException
import app.venues.staff.repository.StaffIdentityRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Service dedicated to recording failed staff login attempts.
 *
 * This service is separate from StaffAuthService to enable proper transaction propagation.
 * When called from StaffAuthService, it creates a NEW transaction that commits independently,
 * ensuring failed login attempts are persisted even when the parent transaction rolls back.
 *
 * Why a separate service?
 * - Spring AOP proxies only work for calls between different beans
 * - Self-invocation (calling @Transactional method from same class) bypasses the proxy
 * - Separate service ensures REQUIRES_NEW propagation actually creates a new transaction
 *
 * Transaction Propagation:
 * - REQUIRES_NEW: Suspends current transaction, creates a new one
 * - New transaction commits independently
 * - Parent transaction rollback doesn't affect this transaction
 */
@Service
class FailedStaffLoginService(
    private val staffRepository: StaffIdentityRepository,
    private val entityManager: EntityManager
) {

    private val logger = KotlinLogging.logger {}

    /**
     * Records a failed login attempt in a separate transaction.
     *
     * This method uses REQUIRES_NEW propagation to ensure the failed attempt
     * is saved even if the parent transaction rolls back (due to authentication failure).
     *
     * Process:
     * 1. Suspend parent transaction (if exists)
     * 2. Create NEW transaction
     * 3. Load staff identity
     * 4. Increment failed login attempts
     * 5. Check if lockout threshold reached
     * 6. Save changes
     * 7. Flush to database
     * 8. COMMIT new transaction
     * 9. Resume parent transaction
     *
     * @param staffId ID of the staff member who failed to login
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordFailedLoginAttempt(staffId: UUID) {
        logger.debug { "Recording failed login attempt for staff $staffId in NEW transaction" }

        // Load staff from database
        val staff = staffRepository.findById(staffId).orElseThrow {
            logger.error { "Staff not found when recording failed login: staffId=$staffId" }
            VenuesException.InternalError(
                "Staff not found when recording failed login",
                "STAFF_NOT_FOUND_FOR_FAILED_LOGIN"
            )
        }

        // Increment failed login attempts (may also lock account)
        staff.recordFailedLogin(
            maxAttempts = AppConstants.Security.MAX_LOGIN_ATTEMPTS,
            lockoutMinutes = AppConstants.Security.LOCKOUT_DURATION_MINUTES
        )

        // Save changes
        staffRepository.save(staff)

        // Force immediate flush to database
        entityManager.flush()

        logger.info {
            "Failed login attempt recorded for staff $staffId (email: ${staff.email}). " +
                    "Total attempts: ${staff.failedLoginAttempts}"
        }
    }
}
