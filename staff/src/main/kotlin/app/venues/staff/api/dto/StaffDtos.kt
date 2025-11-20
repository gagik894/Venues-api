package app.venues.staff.api.dto

import app.venues.staff.domain.StaffRoleLevel
import app.venues.staff.domain.StaffStatus
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.*

/**
 * Response DTO for staff information (public).
 */
data class StaffResponse(
    val id: UUID,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val phoneNumber: String?,
    val role: StaffRoleLevel,
    val status: StaffStatus,
    val emailVerified: Boolean,
    val lastLoginAt: Instant?,
    val createdAt: Instant?,
    val lastModifiedAt: Instant?
)

/**
 * Response DTO for staff information (admin).
 * Includes additional sensitive fields.
 */
data class StaffAdminResponse(
    val id: UUID,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val phoneNumber: String?,
    val role: StaffRoleLevel,
    val status: StaffStatus,
    val emailVerified: Boolean,
    val emailVerifiedAt: Instant?,
    val lastLoginAt: Instant?,
    val failedLoginAttempts: Int,
    val accountLockedUntil: Instant?,
    val restrictedToEventId: UUID?,
    val temporaryAccessExpiresAt: Instant?,
    val createdAt: Instant?,
    val lastModifiedAt: Instant?
)

/**
 * Request DTO to create new staff member.
 */
data class CreateStaffRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be valid")
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, max = 128, message = "Password must be 8-128 characters")
    val password: String,

    @field:Size(min = 2, max = 100, message = "First name must be 2-100 characters")
    val firstName: String? = null,

    @field:Size(min = 2, max = 100, message = "Last name must be 2-100 characters")
    val lastName: String? = null,

    val phoneNumber: String? = null,

    val role: StaffRoleLevel = StaffRoleLevel.VENUE
)

/**
 * Request DTO to update staff information.
 */
data class UpdateStaffRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val phoneNumber: String? = null,
    val role: StaffRoleLevel? = null
)

/**
 * Request DTO for staff login.
 */
data class StaffLoginRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be valid")
    val email: String,

    @field:NotBlank(message = "Password is required")
    val password: String
)

/**
 * Response DTO for successful login.
 */
data class StaffLoginResponse(
    val token: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val staff: StaffResponse
)

/**
 * Request DTO for email verification.
 */
data class VerifyEmailRequest(
    @field:NotBlank(message = "Verification token is required")
    val token: String
)

