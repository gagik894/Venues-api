package app.venues.venue.service

import app.venues.shared.security.jwt.JwtService
import app.venues.venue.api.mapper.VenueMapper
import app.venues.venue.repository.VenueRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Service for venue authentication operations.
 *
 * Handles:
 * - Login authentication with JWT token generation
 * - Failed login attempt tracking
 * - Account locking
 * - Password validation and changes
 *
 * JWT tokens are generated here (in the service), not in the controller,
 * to maintain separation of concerns and follow the same pattern as UserAuthService.
 */
@Service
@Transactional
class VenueAuthService(
    private val venueRepository: VenueRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val venueMapper: VenueMapper,
    private val failedLoginService: FailedVenueLoginService
) {
    private val logger = KotlinLogging.logger {}
    // Note: The login and changePassword methods are commented out for now.
    // TODO: MOVE THESE METHODS TO OWNER AUTH SERVICE WHEN READY
//
//    /**
//     * Authenticate venue with email and password and generate JWT token.
//     *
//     * Process:
//     * 1. Find venue by email
//     * 2. Check account status (active, not locked)
//     * 3. Verify password
//     * 4. Update login tracking
//     * 5. Generate JWT token with "VENUE" prefix to distinguish from user tokens
//     * 6. Return token and venue info
//     *
//     * @param email Venue email
//     * @param password Plain text password
//     * @return VenueLoginResponse with JWT token and venue info
//     * @throws VenuesException.AuthenticationFailure if authentication fails
//     */
//    fun login(email: String, password: String): VenueLoginResponse {
//        logger.debug { "Login attempt for venue: $email" }
//
//        // Find venue by email
//        val venue = venueRepository.findByEmail(email.lowercase())
//            .orElseThrow {
//                logger.warn { "Login failed: Venue not found: $email" }
//                VenuesException.AuthenticationFailure("Invalid email or password")
//            }
//
//        // Check if account is locked
//        if (venue.isAccountLocked()) {
//            logger.warn { "Login failed: Venue account is locked: $email" }
//            throw VenuesException.AuthenticationFailure(
//                "Account is temporarily locked due to multiple failed login attempts. Please try again later."
//            )
//        }
//
//        // Verify password
//        if (!passwordEncoder.matches(password, venue.passwordHash)) {
//            logger.warn { "Login failed: Invalid password for venue: $email" }
//            handleFailedLogin(venue)
//            throw VenuesException.AuthenticationFailure("Invalid email or password")
//        }
//
//        // Check if account is active
//        if (venue.status != VenueStatus.ACTIVE &&
//            venue.status != VenueStatus.PENDING_APPROVAL
//        ) {
//            logger.warn { "Login failed: Venue account is not active: $email (status: ${venue.status})" }
//            throw VenuesException.AuthenticationFailure(
//                "Your account is currently ${venue.status.name.lowercase().replace('_', ' ')}. Please contact support."
//            )
//        }
//
//        // Successful authentication
//        handleSuccessfulLogin(venue)
//
//        val token = jwtService.generateToken(
//            email = venue.email,
//            id = venue.id!!,  // Use venue ID as userId in JWT
//            role = "VENUE"  // Role identifies this as venue token
//        )
//
//        logger.info { "Login successful for venue: $email, ID=${venue.id}" }
//
//        return VenueLoginResponse(
//            token = token,
//            tokenType = "Bearer",
//            expiresIn = jwtService.getExpirationMs(),
//            venue = venueMapper.toResponse(venue)
//        )
//    }
//
//    /**
//     * Change venue password.
//     *
//     * @param venueId ID of the venue
//     * @param currentPassword Current password (for verification)
//     * @param newPassword New password
//     * @throws VenuesException.AuthenticationFailure if current password is incorrect
//     */
//    fun changePassword(venueId: Long, currentPassword: String, newPassword: String) {
//        logger.debug { "Changing password for venue: $venueId" }
//
//        val venue = venueRepository.findById(venueId)
//            .orElseThrow {
//                logger.warn { "Venue not found with ID: $venueId" }
//                VenuesException.ResourceNotFound("Venue not found")
//            }
//
//        // Verify current password
//        if (!passwordEncoder.matches(currentPassword, venue.passwordHash)) {
//            logger.warn { "Invalid current password for venue: ${venue.email}" }
//            throw VenuesException.AuthenticationFailure("Current password is incorrect")
//        }
//
//        // Validate new password is different
//        if (currentPassword == newPassword) {
//            throw VenuesException.ValidationFailure("New password must be different from current password")
//        }
//
//        // Update password
//        venue.passwordHash = passwordEncoder.encode(newPassword)
//        venueRepository.save(venue)
//
//        logger.info { "Password changed successfully for venue: ${venue.email}" }
//    }
//
//    /**
//     * Handle successful login by resetting failed attempts and updating last login timestamp.
//     */
//    private fun handleSuccessfulLogin(venue: Venue) {
//        venue.resetFailedLoginAttempts()
//        venue.lastLoginAt = Instant.now()
//        venueRepository.save(venue)
//    }
//
//    /**
//     * Handle failed login by recording the attempt in a separate transaction.
//     *
//     * The failed attempt is recorded in a NEW transaction (via failedLoginService)
//     * to ensure it persists even if the parent authentication transaction rolls back.
//     */
//    private fun handleFailedLogin(venue: Venue) {
//        // Record failed attempt in separate transaction
//        failedLoginService.recordFailedLoginAttempt(venue.id!!)
//    }
}

