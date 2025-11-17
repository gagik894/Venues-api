package app.venues.venue.service

import app.venues.venue.repository.VenueRepository
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Service dedicated to recording failed login attempts for venues.
 *
 * This service is separate from VenueAuthService to enable proper transaction propagation.
 * When called from VenueAuthService, it creates a NEW transaction that commits independently,
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
class FailedVenueLoginService(
    private val venueRepository: VenueRepository,
    private val entityManager: EntityManager
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Records a failed login attempt in a separate transaction.
     *
     * This method uses REQUIRES_NEW propagation to ensure the failed attempt
     * is saved even if the parent transaction rolls back (due to authentication failure).
     *
     * Process:
     * 1. Suspend parent transaction (if exists)
     * 2. Create NEW transaction
     * 3. Load venue
     * 4. Increment failed login attempts
     * 5. Check if lockout threshold reached
     * 6. Save changes
     * 7. Flush to database
     * 8. COMMIT new transaction
     * 9. Resume parent transaction
     *
     * @param venueId ID of the venue that failed to login
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordFailedLoginAttempt(venueId: Long) {
        //TODO: Implement failed login attempt recording logic
//        logger.debug("Recording failed login attempt for venue {} in NEW transaction", venueId)
//
//        // Load venue from database
//        val venue = venueRepository.findById(venueId).orElseThrow {
//            logger.error("Venue not found when recording failed login: venueId={}", venueId)
//            VenuesException.InternalError("Venue not found when recording failed login")
//        }
//
//        val previousAttempts = venue.failedLoginAttempts
//
//        // Increment failed login attempts (may also lock account)
//        venue.incrementFailedLoginAttempts()
//
//        // Save changes
//        venueRepository.save(venue)
//
//        // Force immediate flush to database
//        entityManager.flush()
//
//        logger.info(
//            "Failed login attempt recorded for venue {} (email: {}). Attempts: {} -> {}{}",
//            venueId,
//            venue.email,
//            previousAttempts,
//            venue.failedLoginAttempts,
//            if (venue.isAccountLocked()) " | Account LOCKED until ${venue.accountLockedUntil}" else ""
//        )
    }
}

