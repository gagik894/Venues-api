package app.venues.staff.service

import app.venues.common.exception.VenuesException
import app.venues.shared.security.jwt.JwtService
import app.venues.staff.api.dto.StaffAuthResponse
import app.venues.staff.api.dto.StaffLoginRequest
import app.venues.staff.api.dto.StaffRegisterRequest
import app.venues.staff.api.dto.VerifyEmailRequest
import app.venues.staff.api.mapper.StaffMapper
import app.venues.staff.domain.StaffIdentity
import app.venues.staff.domain.StaffStatus
import app.venues.staff.repository.StaffIdentityRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

/**
 * Service for staff authentication operations.
 *
 * Responsibilities:
 * - Staff registration with email verification
 * - Login and credential validation
 * - JWT token generation
 * - Failed login attempt tracking
 * - Account lockout management
 * - Email verification
 *
 * Security Features:
 * - Automatic account lockout after failed attempts
 * - Timed lockout release
 * - Last login tracking
 * - Password verification with BCrypt
 */
@Service
class StaffAuthService(
    private val staffRepository: StaffIdentityRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val failedLoginService: FailedStaffLoginService,
    private val staffContextBuilder: StaffContextBuilder
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Registers a new staff identity (global account).
     *
     * Process:
     * 1. Validate email uniqueness
     * 2. Hash password with BCrypt
     * 3. Create staff identity with PENDING_VERIFICATION status
     * 4. Generate verification token
     * 5. Save to database
     * 6. Return auth response
     *
     * @param request Registration request with staff details
     * @return StaffAuthResponse with JWT token and empty context
     * @throws VenuesException.ResourceConflict if email already exists
     */
    @Transactional
    fun register(request: StaffRegisterRequest): StaffAuthResponse {
        logger.info { "Registering new staff: ${request.email}" }

        // Validate email uniqueness
        if (staffRepository.existsByEmail(request.email.lowercase().trim())) {
            logger.warn { "Registration failed: Email already registered: ${request.email}" }
            throw VenuesException.ResourceConflict(
                "A staff account with this email address already exists",
                "EMAIL_ALREADY_EXISTS"
            )
        }

        // Create staff identity
        val staffIdentity = StaffIdentity(
            email = request.email.lowercase().trim(),
            passwordHash = passwordEncoder.encode(request.password),
            firstName = request.firstName.trim(),
            lastName = request.lastName.trim(),
            status = StaffStatus.PENDING_VERIFICATION,
            verificationToken = UUID.randomUUID().toString(),
            verificationTokenExpiresAt = Instant.now().plusSeconds(86400) // 24 hours
        )

        val saved = staffRepository.save(staffIdentity)

        logger.info { "Staff registered successfully: ${saved.email}, ID=${saved.id}" }

        // TODO: Send verification email

        // Generate JWT token
        val token = jwtService.generateToken(
            email = saved.email,
            id = saved.id,
            role = if (saved.isPlatformSuperAdmin) "SUPER_ADMIN" else "STAFF"
        )

        return StaffMapper.toAuthResponse(
            staff = saved,
            context = staffContextBuilder.buildContext(saved),
            token = token,
            expiresIn = jwtService.getExpirationMs()
        )
    }

    /**
     * Authenticates a staff member and generates JWT token.
     *
     * Process:
     * 1. Find staff by email (READ-ONLY transaction)
     * 2. Check account status (active, not locked)
     * 3. Verify password
     * 4. Update login tracking (WRITE transaction)
     * 5. Load organizational context
     * 6. Generate JWT token
     * 7. Return token and context
     *
     * Performance Optimization:
     * - Uses @Transactional(readOnly = true) for initial lookup to reduce lock contention
     * - Only opens write transaction after successful authentication
     *
     * @param request Login credentials
     * @return StaffAuthResponse with JWT token and organizational context
     * @throws VenuesException.AuthenticationFailure if authentication fails
     * @throws VenuesException.AuthorizationFailure if account cannot authenticate
     */
    @Transactional(readOnly = true)
    fun login(request: StaffLoginRequest): StaffAuthResponse {
        logger.info { "Login attempt for staff: ${request.email}" }

        // Find staff by email (read-only)
        val staff = staffRepository.findByEmail(request.email.lowercase().trim())
            ?: run {
                logger.warn { "Login failed: Staff not found: ${request.email}" }
                throw VenuesException.AuthenticationFailure(
                    "Invalid email or password",
                    "INVALID_CREDENTIALS"
                )
            }

        // Check if account can authenticate
        if (!staff.canAuthenticate()) {
            logger.warn { "Login failed: Account cannot authenticate: ${staff.email}, status=${staff.status}" }

            val message = when {
                staff.isAccountLocked() -> "Account is temporarily locked due to too many failed login attempts"
                staff.status == StaffStatus.PENDING_VERIFICATION -> "Email verification required. Please check your email."
                staff.status == StaffStatus.SUSPENDED -> "Account is suspended. Please contact support."
                staff.status == StaffStatus.DELETED -> "Account has been deleted."
                staff.status == StaffStatus.LOCKED -> "Account is locked. Please contact support."
                else -> "Account is not active"
            }

            throw VenuesException.AuthorizationFailure(message, "ACCOUNT_NOT_ACTIVE")
        }

        // Verify password (still in read-only transaction)
        if (!passwordEncoder.matches(request.password, staff.passwordHash)) {
            logger.warn { "Login failed: Invalid password for staff: ${staff.email}" }

            // Record failed login attempt in separate transaction with retry logic
            var attempts = 0
            while (attempts < 3) {
                try {
                    failedLoginService.recordFailedLoginAttempt(staff.id)
                    break
                } catch (e: org.springframework.dao.OptimisticLockingFailureException) {
                    attempts++
                    if (attempts >= 3) {
                        logger.error(e) { "Failed to record login attempt after 3 retries for staff ${staff.id}" }
                    }
                }
            }

            throw VenuesException.AuthenticationFailure(
                "Invalid email or password",
                "INVALID_CREDENTIALS"
            )
        }

        // Successful authentication - now record it in write transaction
        recordSuccessfulLoginInWriteTransaction(staff.id)

        // Generate JWT token
        val token = jwtService.generateToken(
            email = staff.email,
            id = staff.id,
            role = if (staff.isPlatformSuperAdmin) "SUPER_ADMIN" else "STAFF"
        )

        // Load organizational context (uses @EntityGraph for efficiency)
        val context = staffContextBuilder.buildContext(staff)

        logger.info { "Login successful for staff: ${staff.email}, ID=${staff.id}" }

        return StaffMapper.toAuthResponse(
            staff = staff,
            context = context,
            token = token,
            expiresIn = jwtService.getExpirationMs()
        )
    }

    /**
     * Records successful login in a separate write transaction.
     * This method is called after password verification succeeds.
     *
     * @param staffId Staff member ID
     */
    @Transactional
    protected fun recordSuccessfulLoginInWriteTransaction(staffId: UUID) {
        val staff = staffRepository.findById(staffId).orElseThrow {
            VenuesException.InternalError("Staff not found after successful auth", "STAFF_NOT_FOUND")
        }
        staff.recordSuccessfulLogin()
        staffRepository.save(staff)
    }

    /**
     * Verifies email using verification token.
     *
     * Process:
     * 1. Find staff by verification token
     * 2. Check token expiration
     * 3. Verify email
     * 4. Save changes
     *
     * @param request Verification token
     * @throws VenuesException.ResourceNotFound if token is invalid
     * @throws VenuesException.BusinessRuleViolation if token is expired
     */
    @Transactional
    fun verifyEmail(request: VerifyEmailRequest) {
        logger.info { "Verifying email with token" }

        val staff = staffRepository.findByVerificationToken(request.token)
            ?: throw VenuesException.ResourceNotFound(
                "Invalid or expired verification token",
                "INVALID_TOKEN"
            )

        // Check token expiration
        if (staff.verificationTokenExpiresAt?.isBefore(Instant.now()) == true) {
            throw VenuesException.BusinessRuleViolation(
                "Verification token has expired",
                "TOKEN_EXPIRED"
            )
        }

        // Verify email
        staff.verifyEmail()
        staffRepository.save(staff)

        logger.info { "Email verified successfully for: ${staff.email}" }
    }
}
