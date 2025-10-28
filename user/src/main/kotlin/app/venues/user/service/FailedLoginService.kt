/*
 * Venues API - Government-Sponsored Cultural Venues Portal
 * Copyright (c) 2025 Government Cultural Department
 *
 * Service for recording failed login attempts in a separate transaction.
 */

package app.venues.user.service

import app.venues.common.exception.VenuesException
import app.venues.user.repository.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Service dedicated to recording failed login attempts.
 *
 * This service is separate from UserAuthService to enable proper transaction propagation.
 * When called from UserAuthService, it creates a NEW transaction that commits independently,
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
class FailedLoginService(
    private val userRepository: UserRepository,
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
     * 3. Load user
     * 4. Increment failed login attempts
     * 5. Check if lockout threshold reached
     * 6. Save changes
     * 7. Flush to database
     * 8. COMMIT new transaction
     * 9. Resume parent transaction
     *
     * @param userId ID of the user who failed to login
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordFailedLoginAttempt(userId: Long) {
        logger.debug { "Recording failed login attempt for user $userId in NEW transaction" }

        // Load user from database
        val user = userRepository.findById(userId).orElseThrow {
            logger.error { "User not found when recording failed login: userId=$userId" }
            VenuesException.InternalError(
                "User not found when recording failed login",
                "USER_NOT_FOUND_FOR_FAILED_LOGIN"
            )
        }

        val previousAttempts = user.failedLoginAttempts

        // Increment failed login attempts (may also lock account)
        user.recordFailedLogin()

        // Save changes
        userRepository.save(user)

        // Force immediate flush to database
        entityManager.flush()

        logger.info {
            "Failed login attempt recorded for user $userId (email: ${user.email}). " +
                    "Attempts: $previousAttempts -> ${user.failedLoginAttempts}" +
                    if (user.isAccountLocked()) " | Account LOCKED until ${user.lockedUntil}" else ""
        }
    }
}

