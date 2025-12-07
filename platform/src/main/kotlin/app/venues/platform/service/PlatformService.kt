package app.venues.platform.service

import app.venues.common.exception.VenuesException
import app.venues.platform.api.dto.*
import app.venues.platform.domain.Platform
import app.venues.platform.repository.PlatformRepository
import app.venues.platform.repository.WebhookEventRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.util.*

/**
 * Service for platform management (admin operations only).
 *
 * Platform booking operations (/hold, /checkout, /confirm) are now in PlatformBookingService.
 * This service handles only platform CRUD operations for admin users.
 */
@Service
@Transactional
class PlatformService(
    private val platformRepository: PlatformRepository,
    private val webhookEventRepository: WebhookEventRepository
) {
    private val logger = KotlinLogging.logger {}

    companion object {
        private const val SECRET_LENGTH = 64
    }

    // ===========================================
    // PLATFORM MANAGEMENT (Admin Operations)
    // ===========================================

    /**
     * Create a new platform
     */
    fun createPlatform(request: CreatePlatformRequest): PlatformWithSecretResponse {
        logger.debug { "Creating platform: ${request.name}" }

        // Check if platform already exists
        if (platformRepository.existsByName(request.name)) {
            throw VenuesException.ResourceConflict("Platform with name '${request.name}' already exists")
        }

        // Generate shared secret
        val sharedSecret = generateSharedSecret()

        // Create platform
        val platform = Platform(
            name = request.name,
            apiUrl = request.apiUrl,
            sharedSecret = sharedSecret,
            description = request.description,
            contactEmail = request.contactEmail,
            rateLimit = request.rateLimit
        )

        val saved = platformRepository.save(platform)
        logger.info { "Platform created: ${saved.name} (ID: ${saved.id})" }

        return toPlatformWithSecretResponse(saved)
    }

    /**
     * Update platform
     */
    fun updatePlatform(id: UUID, request: UpdatePlatformRequest): PlatformResponse {
        logger.debug { "Updating platform: $id" }

        val platform = platformRepository.findById(id)
            .orElseThrow { VenuesException.ResourceNotFound("Platform not found") }

        // Update fields
        request.apiUrl?.let { platform.apiUrl = it }
        request.description?.let { platform.description = it }
        request.contactEmail?.let { platform.contactEmail = it }
        request.rateLimit?.let { platform.rateLimit = it }

        val updated = platformRepository.save(platform)
        logger.info { "Platform updated: ${updated.name}" }

        return toPlatformResponse(updated)
    }

    /**
     * Regenerate platform shared secret
     */
    fun regenerateSharedSecret(id: UUID, request: RegenerateSecretRequest): PlatformWithSecretResponse {
        logger.debug { "Regenerating shared secret for platform: $id" }

        if (!request.confirm) {
            throw VenuesException.ValidationFailure("Confirmation required to regenerate secret")
        }

        val platform = platformRepository.findById(id)
            .orElseThrow { VenuesException.ResourceNotFound("Platform not found") }

        // Generate new secret
        platform.sharedSecret = generateSharedSecret()
        val updated = platformRepository.save(platform)

        logger.warn { "Shared secret regenerated for platform: ${platform.name}" }

        return toPlatformWithSecretResponse(updated)
    }

    /**
     * Get platform by ID
     */
    @Transactional(readOnly = true)
    fun getPlatformById(id: UUID): PlatformResponse {
        val platform = platformRepository.findById(id)
            .orElseThrow { VenuesException.ResourceNotFound("Platform not found") }
        return toPlatformResponse(platform)
    }

    /**
     * Get all platforms
     */
    @Transactional(readOnly = true)
    fun getAllPlatforms(pageable: Pageable): Page<PlatformResponse> {
        return platformRepository.findAll(pageable).map { toPlatformResponse(it) }
    }

    /**
     * Delete platform
     */
    fun deletePlatform(id: UUID) {
        logger.debug { "Deleting platform: $id" }
        val platform = platformRepository.findById(id)
            .orElseThrow { VenuesException.ResourceNotFound("Platform not found") }
        platformRepository.delete(platform)
        logger.info { "Platform deleted: ${platform.name}" }
    }

    // ===========================================
    // HELPER METHODS
    // ===========================================

    /**
     * Generate cryptographically secure shared secret
     */
    private fun generateSharedSecret(): String {
        val random = SecureRandom()
        val bytes = ByteArray(SECRET_LENGTH)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Convert Platform to response DTO
     */
    private fun toPlatformResponse(platform: Platform): PlatformResponse {
        return PlatformResponse(
            id = platform.id,
            name = platform.name,
            apiUrl = platform.apiUrl,
            status = platform.status,
            webhookEnabled = platform.webhookEnabled,
            description = platform.description,
            contactEmail = platform.contactEmail,
            rateLimit = platform.rateLimit,
            webhookSuccessCount = platform.webhookSuccessCount,
            webhookFailureCount = platform.webhookFailureCount,
            lastWebhookSuccess = platform.lastWebhookSuccess?.toString(),
            lastWebhookFailure = platform.lastWebhookFailure?.toString(),
            createdAt = platform.createdAt.toString(),
            lastModifiedAt = platform.lastModifiedAt.toString()
        )
    }

    /**
     * Convert Platform to response with secret (only used once after creation/regeneration)
     */
    private fun toPlatformWithSecretResponse(platform: Platform): PlatformWithSecretResponse {
        return PlatformWithSecretResponse(
            id = requireNotNull(platform.id) { "Platform ID must be set" },
            name = platform.name,
            apiUrl = platform.apiUrl,
            sharedSecret = platform.sharedSecret,
            status = platform.status,
            webhookEnabled = platform.webhookEnabled,
            description = platform.description,
            contactEmail = platform.contactEmail,
            rateLimit = platform.rateLimit,
            createdAt = platform.createdAt.toString()
        )
    }
}
