package app.venues.user.service

import app.venues.common.exception.VenuesException
import app.venues.user.api.UserApi
import app.venues.user.api.dto.UserBasicInfoDto
import app.venues.user.api.dto.UserRegistrationRequest
import app.venues.user.api.dto.UserUpdateRequest
import app.venues.user.domain.User
import app.venues.user.domain.UserRole
import app.venues.user.domain.UserStatus
import app.venues.user.repository.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Service class for User domain business logic.
 *
 * This is the ADAPTER in Hexagonal Architecture.
 * Implements UserApi (the PORT) to provide a stable public API for other modules.
 *
 * Responsibilities:
 * - User registration and profile management
 * - Password management
 * - Account status management
 * - Business rule enforcement
 * - Cross-module API (via UserApi implementation)
 */
@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) : UserApi {

    private val logger = KotlinLogging.logger {}

    // ===========================================
    // PUBLIC API IMPLEMENTATION (UserApi Port)
    // ===========================================

    override fun getUserBasicInfo(userId: Long): UserBasicInfoDto? {
        return userRepository.findById(userId)
            .map { user ->
                UserBasicInfoDto(
                    id = user.id!!,
                    email = user.email,
                    firstName = user.firstName,
                    lastName = user.lastName,
                    phoneNumber = user.phoneNumber
                )
            }
            .orElse(null)
    }

    override fun getUserEmail(userId: Long): String? {
        return userRepository.findById(userId)
            .map { it.email }
            .orElse(null)
    }

    override fun getUserFullName(userId: Long): String? {
        return userRepository.findById(userId)
            .map { "${it.firstName} ${it.lastName}" }
            .orElse(null)
    }

    override fun userExists(userId: Long): Boolean {
        return userRepository.existsById(userId)
    }

    // ===========================================
    // INTERNAL BUSINESS LOGIC
    // ===========================================

    /**
     * Registers a new user account.
     *
     * Process:
     * 1. Validate email uniqueness
     * 2. Hash password with BCrypt
     * 3. Create user entity with default values
     * 4. Save to database
     * 5. Return created user
     *
     * @param request Registration request with user details
     * @return Newly created User entity
     * @throws VenuesException.ResourceConflict if email already exists
     */
    @Transactional
    fun registerUser(request: UserRegistrationRequest): User {
        logger.info { "Registering new user with email: ${request.email}" }

        // Check if email already exists
        if (userRepository.existsByEmail(request.email)) {
            logger.warn { "Registration failed: Email already exists: ${request.email}" }
            throw VenuesException.ResourceConflict(
                "A user with this email address already exists",
                "EMAIL_ALREADY_EXISTS"
            )
        }

        // Create new user entity
        val user = User(
            email = request.email.lowercase().trim(),
            passwordHash = passwordEncoder.encode(request.password),
            firstName = request.firstName.trim(),
            lastName = request.lastName.trim(),
            phoneNumber = request.phoneNumber?.trim(),
            role = UserRole.USER,
            status = UserStatus.PENDING_VERIFICATION,
            emailVerified = false
        )

        // Save to database
        val savedUser = userRepository.save(user)

        logger.info { "User registered successfully: ID=${savedUser.id}, email=${savedUser.email}" }

        // TODO: Send verification email

        return savedUser
    }

    /**
     * Finds a user by ID.
     *
     * @param userId User ID to search for
     * @return User entity
     * @throws VenuesException.ResourceNotFound if user doesn't exist
     */
    fun getUserById(userId: Long): User {
        return userRepository.findById(userId)
            .orElseThrow {
                logger.warn { "User not found: ID=$userId" }
                VenuesException.ResourceNotFound(
                    "User not found with ID: $userId",
                    "USER_NOT_FOUND"
                )
            }
    }

    /**
     * Finds a user by email address.
     *
     * @param email Email address to search for
     * @return User entity
     * @throws VenuesException.ResourceNotFound if user doesn't exist
     */
    fun getUserByEmail(email: String): User {
        return userRepository.findByEmail(email.lowercase().trim())
            .orElseThrow {
                logger.warn { "User not found: email=$email" }
                VenuesException.ResourceNotFound(
                    "User not found with email: $email",
                    "USER_NOT_FOUND"
                )
            }
    }

    /**
     * Updates user profile information.
     *
     * Only updates provided fields (null fields are ignored).
     *
     * @param userId ID of user to update
     * @param request Update request with new values
     * @return Updated User entity
     * @throws VenuesException.ResourceNotFound if user doesn't exist
     */
    @Transactional
    fun updateUserProfile(userId: Long, request: UserUpdateRequest): User {
        logger.info { "Updating profile for user: ID=$userId" }

        val user = getUserById(userId)

        // Update only provided fields
        request.firstName?.let { user.firstName = it.trim() }
        request.lastName?.let { user.lastName = it.trim() }
        request.phoneNumber?.let { user.phoneNumber = it.trim() }

        val updatedUser = userRepository.save(user)

        logger.info { "User profile updated: ID=$userId" }

        return updatedUser
    }

    /**
     * Changes user password.
     *
     * Validates current password before allowing change.
     *
     * @param userId ID of user changing password
     * @param currentPassword Current password for verification
     * @param newPassword New password to set
     * @throws VenuesException.AuthenticationFailure if current password is wrong
     * @throws VenuesException.BusinessRuleViolation if new password same as current
     */
    @Transactional
    fun changePassword(userId: Long, currentPassword: String, newPassword: String) {
        logger.info { "Changing password for user: ID=$userId" }

        val user = getUserById(userId)

        // Verify current password
        if (!passwordEncoder.matches(currentPassword, user.passwordHash)) {
            logger.warn { "Password change failed: Invalid current password for user: ID=$userId" }
            throw VenuesException.AuthenticationFailure(
                "Current password is incorrect",
                "INVALID_CURRENT_PASSWORD"
            )
        }

        // Check new password is different
        if (passwordEncoder.matches(newPassword, user.passwordHash)) {
            throw VenuesException.BusinessRuleViolation(
                "New password must be different from current password",
                "PASSWORD_SAME_AS_CURRENT"
            )
        }

        // Update password
        user.passwordHash = passwordEncoder.encode(newPassword)
        userRepository.save(user)

        logger.info { "Password changed successfully for user: ID=$userId" }
    }

    /**
     * Deactivates a user account (soft delete).
     *
     * @param userId ID of user to deactivate
     */
    @Transactional
    fun deactivateUser(userId: Long) {
        logger.info { "Deactivating user: ID=$userId" }

        val user = getUserById(userId)
        user.status = UserStatus.DELETED
        userRepository.save(user)

        logger.info { "User deactivated: ID=$userId" }
    }

    /**
     * Verifies user email address.
     *
     * Marks email as verified and activates account.
     *
     * @param userId ID of user to verify
     */
    @Transactional
    fun verifyEmail(userId: Long) {
        logger.info { "Verifying email for user: ID=$userId" }

        val user = getUserById(userId)
        user.emailVerified = true
        user.status = UserStatus.ACTIVE
        userRepository.save(user)

        logger.info { "Email verified for user: ID=$userId" }
    }

    /**
     * Gets all users (admin operation).
     *
     * @return List of all users
     */
    fun getAllUsers(): List<User> {
        logger.debug { "Fetching all users" }
        return userRepository.findAll()
    }

    /**
     * Gets all active users.
     *
     * @return List of active users
     */
    fun getAllActiveUsers(): List<User> {
        logger.debug { "Fetching all active users" }
        return userRepository.findAllActiveUsers()
    }
}

