package app.venues.platform.api.dto

import app.venues.platform.domain.PlatformStatus
import jakarta.validation.constraints.*
import java.util.*

// ===========================================
// PLATFORM MANAGEMENT REQUEST DTOs
// ===========================================

/**
 * Request to create a new platform
 */
data class CreatePlatformRequest(
    @field:NotBlank(message = "Platform name is required")
    @field:Size(max = 100, message = "Platform name must not exceed 100 characters")
    var name: String,

    @field:NotBlank(message = "API URL is required")
    @field:Size(max = 500, message = "API URL must not exceed 500 characters")
    @field:Pattern(regexp = "^https://.*", message = "API URL must use HTTPS")
    var apiUrl: String,

    @field:Size(max = 1000, message = "Description must not exceed 1000 characters")
    var description: String? = null,

    @field:Email(message = "Invalid email format")
    var contactEmail: String? = null,

    @field:Min(value = 1, message = "Rate limit must be at least 1")
    @field:Max(value = 10000, message = "Rate limit must not exceed 10000")
    var rateLimit: Int? = null,

    var webhookEnabled: Boolean = true
)

/**
 * Request to update a platform
 */
data class UpdatePlatformRequest(
    @field:Size(max = 500, message = "API URL must not exceed 500 characters")
    @field:Pattern(regexp = "^$|^https://.*", message = "API URL must be empty or use HTTPS")
    var apiUrl: String? = null,

    var status: PlatformStatus? = null,

    var webhookEnabled: Boolean? = null,

    @field:Size(max = 1000, message = "Description must not exceed 1000 characters")
    var description: String? = null,

    @field:Email(message = "Invalid email format")
    var contactEmail: String? = null,

    @field:Min(value = 1, message = "Rate limit must be at least 1")
    @field:Max(value = 10000, message = "Rate limit must not exceed 10000")
    var rateLimit: Int? = null
)

/**
 * Request to regenerate platform shared secret
 */
data class RegenerateSecretRequest(
    @field:NotNull(message = "Confirmation is required")
    var confirm: Boolean
)

// ===========================================
// PLATFORM MANAGEMENT RESPONSE DTOs
// ===========================================

/**
 * Platform details response
 */
data class PlatformResponse(
    val id: UUID,
    val name: String,
    val apiUrl: String,
    val status: PlatformStatus,
    val webhookEnabled: Boolean,
    val description: String?,
    val contactEmail: String?,
    val rateLimit: Int?,
    val webhookSuccessCount: Long,
    val webhookFailureCount: Long,
    val lastWebhookSuccess: String?,
    val lastWebhookFailure: String?,
    val createdAt: String,
    val lastModifiedAt: String
)

/**
 * Platform with shared secret (only shown once after creation or regeneration)
 */
data class PlatformWithSecretResponse(
    val id: UUID,
    val name: String,
    val apiUrl: String,
    val sharedSecret: String,  // Only included in this response
    val status: PlatformStatus,
    val webhookEnabled: Boolean,
    val description: String?,
    val contactEmail: String?,
    val rateLimit: Int?,
    val createdAt: String
)

