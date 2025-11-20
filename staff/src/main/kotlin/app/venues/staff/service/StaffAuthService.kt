package app.venues.staff.service

import app.venues.common.exception.VenuesException
import app.venues.shared.security.jwt.JwtService
import app.venues.staff.api.dto.*
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
 * - Staff registration
 * - Login and credential validation
 * - JWT token generation
 * - Email verification
 * - Failed login tracking
 * - Account lockout management
 */
@Service
@Transactional
class StaffAuthService(
    private val staffRepository: StaffIdentityRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val staffContextBuilder: StaffContextBuilder
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Registers a new staff identity (global account).
     *
     * Process:
     * 1. Validate email uniqueness
     * 2. Hash password
     * 3. Create staff identity
     * 4. Generate verification token
     * 5. Return response with token and empty context
     */
    fun register(request: StaffRegisterRequest): StaffAuthResponse {
        logger.info { "Registering new staff: ${request.email}" }

        // Validate email uniqueness
        if (staffRepository.existsByEmail(request.email.lowercase().trim())) {
            throw VenuesException.ResourceConflict(
                "Email already registered",
                "EMAIL_EXISTS"
            )
        }

        // Create staff identity
        val staffIdentity = StaffIdentity(
            email = request.email.lowercase().trim(),
            passwordHash = passwordEncoder.encode(request.password),
            firstName = request.firstName,
            lastName = request.lastName,
            status = StaffStatus.PENDING_VERIFICATION,
            verificationToken = UUID.randomUUID().toString(),
            verificationTokenExpiresAt = Instant.now().plusSeconds(86400) // 24 hours
        )

        val saved = staffRepository.save(staffIdentity)

        logger.info { "Staff registered successfully: ${saved.email}, ID=${saved.id}" }

        // Generate JWT (even for unverified accounts, they just can't do much)
        val token = jwtService.generateToken(
            email = saved.email,
            id = saved.id,
            role = if (saved.isPlatformSuperAdmin) "SUPER_ADMIN" else "STAFF"
        )

        return StaffAuthResponse(
            token = token,
            expiresIn = jwtService.getExpirationMs(),
            profile = toProfileDto(saved),
            context = StaffGlobalContextDto(emptyList()) // No memberships yet
        )
    }

    /**
     * Authenticates a staff member and generates JWT token.
     *
     * Process:
     * 1. Find staff by email
     * 2. Check account status
     * 3. Verify password
     * 4. Update login tracking
     * 5. Load organizational context
     * 6. Generate JWT
     */
    fun login(request: StaffLoginRequest): StaffAuthResponse {
        logger.info { "Login attempt for staff: ${request.email}" }

        // Find staff by email
        val staff = staffRepository.findByEmail(request.email.lowercase().trim())
            ?: throw VenuesException.AuthenticationFailure(
                "Invalid email or password",
                "INVALID_CREDENTIALS"
            )

        // Check account status
        if (staff.isAccountLocked()) {
            throw VenuesException.AuthorizationFailure(
                "Account is locked due to too many failed login attempts",
                "ACCOUNT_LOCKED"
            )
        }

        if (staff.status !in listOf(StaffStatus.ACTIVE, StaffStatus.PENDING_VERIFICATION)) {
            val message = when (staff.status) {
                StaffStatus.SUSPENDED -> "Account is suspended"
                StaffStatus.DELETED -> "Account has been deleted"
                else -> "Account is not active"
            }
            throw VenuesException.AuthorizationFailure(message, "ACCOUNT_NOT_ACTIVE")
        }

        // Verify password
        if (!passwordEncoder.matches(request.password, staff.passwordHash)) {
            logger.warn { "Login failed: Invalid password for ${staff.email}" }
            staff.recordFailedLoginAttempt()
            staffRepository.save(staff)
            throw VenuesException.AuthenticationFailure(
                "Invalid email or password",
                "INVALID_CREDENTIALS"
            )
        }

        // Successful login
        staff.recordSuccessfulLogin()
        staffRepository.save(staff)

        // Generate JWT
        val token = jwtService.generateToken(
            email = staff.email,
            id = staff.id,
            role = if (staff.isPlatformSuperAdmin) "SUPER_ADMIN" else "STAFF"
        )

        // Load context (organizations and venues)
        val context = staffContextBuilder.buildContext(staff)

        logger.info { "Login successful for staff: ${staff.email}, ID=${staff.id}" }

        return StaffAuthResponse(
            token = token,
            expiresIn = jwtService.getExpirationMs(),
            profile = toProfileDto(staff),
            context = context
        )
    }

    /**
     * Verifies email using token.
     */
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

    private fun toProfileDto(staff: StaffIdentity): StaffProfileDto {
        return StaffProfileDto(
            id = staff.id,
            email = staff.email,
            firstName = staff.firstName,
            lastName = staff.lastName,
            status = staff.status,
            isPlatformSuperAdmin = staff.isPlatformSuperAdmin
        )
    }
}
