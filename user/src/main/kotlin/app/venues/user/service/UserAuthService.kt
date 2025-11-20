package app.venues.user.service

import app.venues.common.exception.VenuesException
import app.venues.shared.security.jwt.JwtService
import app.venues.user.api.dto.LoginRequest
import app.venues.user.api.dto.LoginResponse
import app.venues.user.api.mapper.UserMapper
import app.venues.user.domain.UserStatus
import app.venues.user.repository.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Service handling user authentication operations.
 *
 * Responsibilities:
 * - User login and credential validation
 * - JWT token generation
 * - Failed login attempt tracking
 * - Account lockout management
 *
 * Security Features:
 * - Automatic account lockout after failed attempts
 * - Timed lockout release
 * - Last login tracking
 * - Password verification with BCrypt
 */
@Service
class UserAuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val failedLoginService: FailedLoginService
) {

    private val logger = KotlinLogging.logger {}

    /**
     * Authenticates a user and generates JWT token.
     *
     * Process:
     * 1. Find user by email (READ-ONLY transaction)
     * 2. Check account status (active, not locked)
     * 3. Verify password
     * 4. Update login tracking (WRITE transaction)
     * 5. Generate JWT token
     * 6. Return token and user info
     *
     * Performance Optimization:
     * - Uses @Transactional(readOnly = true) for initial lookup to reduce lock contention
     * - Only opens write transaction after successful authentication
     *
     * @param request Login credentials
     * @return LoginResponse with JWT token and user info
     * @throws VenuesException.AuthenticationFailure if authentication fails
     */
    @Transactional(readOnly = true)
    fun login(request: LoginRequest): LoginResponse {
        logger.info { "Login attempt for user: ${request.email}" }

        // Find user by email (read-only)
        val user = userRepository.findByEmail(request.email.lowercase().trim())
            .orElseThrow {
                logger.warn { "Login failed: User not found: ${request.email}" }
                VenuesException.AuthenticationFailure(
                    "Invalid email or password",
                    "INVALID_CREDENTIALS"
                )
            }

        // Check if account can authenticate
        if (!user.canAuthenticate()) {
            logger.warn { "Login failed: Account cannot authenticate: ${user.email}, status=${user.status}" }

            val message = when {
                user.isAccountLocked() -> "Account is temporarily locked due to too many failed login attempts"
                user.status == UserStatus.PENDING_VERIFICATION -> "Email verification required. Please check your email."
                user.status == UserStatus.SUSPENDED -> "Account is suspended. Please contact support."
                user.status == UserStatus.DELETED -> "Account has been deleted."
                else -> "Account is not active"
            }

            throw VenuesException.AuthorizationFailure(message, "ACCOUNT_NOT_ACTIVE")
        }

        // Verify password (still in read-only transaction)
        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            logger.warn { "Login failed: Invalid password for user: ${user.email}" }

            // Record failed login attempt in separate transaction with retry logic
            var attempts = 0
            while (attempts < 3) {
                try {
                    failedLoginService.recordFailedLoginAttempt(user.id)
                    break
                } catch (e: org.springframework.dao.OptimisticLockingFailureException) {
                    attempts++
                    if (attempts >= 3) {
                        logger.error(e) { "Failed to record login attempt after 3 retries for user ${user.id}" }
                    }
                }
            }

            throw VenuesException.AuthenticationFailure(
                "Invalid email or password",
                "INVALID_CREDENTIALS"
            )
        }

        // Successful authentication - now record it in write transaction
        recordSuccessfulLoginInWriteTransaction(user.id)

        // Generate JWT token
        val token = jwtService.generateToken(
            email = user.email,
            id = user.id,
            role = "USER"  // All users are customers with USER role
        )

        logger.info { "Login successful for user: ${user.email}, ID=${user.id}" }

        return LoginResponse(
            token = token,
            tokenType = "Bearer",
            expiresIn = jwtService.getExpirationMs(),
            user = UserMapper.toResponse(user)
        )
    }

    /**
     * Records successful login in a separate write transaction.
     * This method is called after password verification succeeds.
     *
     * @param userId User ID
     */
    @Transactional
    protected fun recordSuccessfulLoginInWriteTransaction(userId: UUID) {
        val user = userRepository.findById(userId).orElseThrow {
            VenuesException.InternalError("User not found after successful auth", "USER_NOT_FOUND")
        }
        user.recordSuccessfulLogin()
        userRepository.save(user)
    }
}
