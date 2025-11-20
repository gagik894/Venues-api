package app.venues.user.api.dto

import app.venues.common.constants.AppConstants
import app.venues.user.domain.UserStatus
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.*

/**
 * Request DTO for user registration.
 *
 * Validates all required fields before account creation.
 * Passwords are validated but never returned in responses.
 */
data class UserRegistrationRequest(

    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be valid")
    @field:Size(max = 255, message = "Email must not exceed 255 characters")
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(
        min = AppConstants.Validation.MIN_PASSWORD_LENGTH,
        max = AppConstants.Validation.MAX_PASSWORD_LENGTH,
        message = "Password must be between ${AppConstants.Validation.MIN_PASSWORD_LENGTH} and ${AppConstants.Validation.MAX_PASSWORD_LENGTH} characters"
    )
    val password: String,

    @field:NotBlank(message = "First name is required")
    @field:Size(
        min = AppConstants.Validation.MIN_NAME_LENGTH,
        max = AppConstants.Validation.MAX_NAME_LENGTH,
        message = "First name must be between ${AppConstants.Validation.MIN_NAME_LENGTH} and ${AppConstants.Validation.MAX_NAME_LENGTH} characters"
    )
    val firstName: String,

    @field:NotBlank(message = "Last name is required")
    @field:Size(
        min = AppConstants.Validation.MIN_NAME_LENGTH,
        max = AppConstants.Validation.MAX_NAME_LENGTH,
        message = "Last name must be between ${AppConstants.Validation.MIN_NAME_LENGTH} and ${AppConstants.Validation.MAX_NAME_LENGTH} characters"
    )
    val lastName: String,

    @field:Pattern(
        regexp = AppConstants.Patterns.PHONE,
        message = "Phone number format is invalid"
    )
    val phoneNumber: String? = null
)

/**
 * Request DTO for user login.
 *
 * Simple credentials for authentication.
 */
data class LoginRequest(

    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be valid")
    val email: String,

    @field:NotBlank(message = "Password is required")
    val password: String
)

/**
 * Response DTO for successful authentication.
 *
 * Contains JWT token and user information.
 */
data class LoginResponse(
    val token: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val user: UserResponse
)

/**
 * Request DTO for updating user profile.
 *
 * All fields are optional - only provided fields will be updated.
 */
data class UserUpdateRequest(

    @field:Size(
        min = AppConstants.Validation.MIN_NAME_LENGTH,
        max = AppConstants.Validation.MAX_NAME_LENGTH,
        message = "First name must be between ${AppConstants.Validation.MIN_NAME_LENGTH} and ${AppConstants.Validation.MAX_NAME_LENGTH} characters"
    )
    val firstName: String? = null,

    @field:Size(
        min = AppConstants.Validation.MIN_NAME_LENGTH,
        max = AppConstants.Validation.MAX_NAME_LENGTH,
        message = "Last name must be between ${AppConstants.Validation.MIN_NAME_LENGTH} and ${AppConstants.Validation.MAX_NAME_LENGTH} characters"
    )
    val lastName: String? = null,

    @field:Pattern(
        regexp = AppConstants.Patterns.PHONE,
        message = "Phone number format is invalid"
    )
    val phoneNumber: String? = null
)

/**
 * Request DTO for password change.
 *
 * Requires current password for security.
 */
data class PasswordChangeRequest(

    @field:NotBlank(message = "Current password is required")
    val currentPassword: String,

    @field:NotBlank(message = "New password is required")
    @field:Size(
        min = AppConstants.Validation.MIN_PASSWORD_LENGTH,
        max = AppConstants.Validation.MAX_PASSWORD_LENGTH,
        message = "Password must be between ${AppConstants.Validation.MIN_PASSWORD_LENGTH} and ${AppConstants.Validation.MAX_PASSWORD_LENGTH} characters"
    )
    val newPassword: String
)

/**
 * Response DTO for user information.
 *
 * Safe representation of user data without sensitive information.
 * Never includes password hash or security-related fields.
 */
data class UserResponse(
    val id: UUID,
    val email: String,
    val firstName: String,
    val lastName: String,
    val fullName: String,
    val phoneNumber: String?,
    val status: UserStatus,
    val emailVerified: Boolean,
    val lastLoginAt: Instant?,
    val createdAt: Instant,
    val lastModifiedAt: Instant
)

