package app.venues.user.api.dto

/**
 * Basic user information DTO for cross-module communication.
 *
 * This is a stable data contract that will not change frequently.
 * Contains only essential fields needed by other modules.
 */
data class UserBasicInfoDto(
    val id: Long,
    val email: String,
    val firstName: String,
    val lastName: String,
    val phoneNumber: String?
)

