package app.venues.staff.api.dto

import app.venues.common.constants.AppConstants
import app.venues.staff.domain.StaffStatus
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.*

/**
 * Request DTO for staff email verification.
 */
data class VerifyEmailRequest(
    @field:NotBlank(message = "Verification token is required")
    val token: String
)

/**
 * Request DTO for updating staff status (admin operation).
 */
data class UpdateStaffStatusRequest(
    val staffId: UUID,
    val status: StaffStatus
)

/**
 * Request DTO for staff registration.
 *
 * Validates all required fields before account creation.
 * Passwords are validated but never returned in responses.
 */
data class StaffRegisterRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be valid")
    @field:Size(max = 255, message = "Email must not exceed 255 characters")
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(
        min = AppConstants.Validation.MIN_PASSWORD_LENGTH,
        max = AppConstants.Validation.MAX_PASSWORD_LENGTH,
        message = "Password must be between \${AppConstants.Validation.MIN_PASSWORD_LENGTH} and \${AppConstants.Validation.MAX_PASSWORD_LENGTH} characters"
    )
    val password: String,

    @field:NotBlank(message = "First name is required")
    @field:Size(
        min = AppConstants.Validation.MIN_NAME_LENGTH,
        max = AppConstants.Validation.MAX_NAME_LENGTH,
        message = "First name must be between \${AppConstants.Validation.MIN_NAME_LENGTH} and \${AppConstants.Validation.MAX_NAME_LENGTH} characters"
    )
    val firstName: String,

    @field:NotBlank(message = "Last name is required")
    @field:Size(
        min = AppConstants.Validation.MIN_NAME_LENGTH,
        max = AppConstants.Validation.MAX_NAME_LENGTH,
        message = "Last name must be between \${AppConstants.Validation.MIN_NAME_LENGTH} and \${AppConstants.Validation.MAX_NAME_LENGTH} characters"
    )
    val lastName: String
)

/**
 * Request DTO for staff login.
 *
 * Simple credentials for authentication.
 */
data class StaffLoginRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be valid")
    val email: String,

    @field:NotBlank(message = "Password is required")
    val password: String
)
