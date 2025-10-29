package app.venues.platform.service

import app.venues.booking.api.dto.AddGAToCartRequest
import app.venues.booking.api.dto.AddSeatToCartRequest
import app.venues.booking.service.CartService
import app.venues.common.exception.VenuesException
import app.venues.platform.api.dto.*
import app.venues.platform.domain.Platform
import app.venues.platform.domain.PlatformStatus
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
 * Service for platform management and API operations.
 */
@Service
@Transactional
class PlatformService(
    private val platformRepository: PlatformRepository,
    private val webhookEventRepository: WebhookEventRepository,
    private val cartService: CartService
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
            status = PlatformStatus.ACTIVE,
            webhookEnabled = request.webhookEnabled,
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
    fun updatePlatform(id: Long, request: UpdatePlatformRequest): PlatformResponse {
        logger.debug { "Updating platform: $id" }

        val platform = platformRepository.findById(id)
            .orElseThrow { VenuesException.ResourceNotFound("Platform not found") }

        // Update fields
        request.apiUrl?.let { platform.apiUrl = it }
        request.status?.let { platform.status = it }
        request.webhookEnabled?.let { platform.webhookEnabled = it }
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
    fun regenerateSharedSecret(id: Long, request: RegenerateSecretRequest): PlatformWithSecretResponse {
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
    fun getPlatformById(id: Long): PlatformResponse {
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
    fun deletePlatform(id: Long) {
        logger.debug { "Deleting platform: $id" }

        val platform = platformRepository.findById(id)
            .orElseThrow { VenuesException.ResourceNotFound("Platform not found") }

        platformRepository.delete(platform)
        logger.info { "Platform deleted: ${platform.name}" }
    }

    // ===========================================
    // PLATFORM API OPERATIONS (External Platforms)
    // ===========================================

    /**
     * Reserve seats for external platform
     */
    fun reserveSeats(
        platformId: Long,
        request: PlatformReservationRequest
    ): PlatformReservationResponse {
        logger.debug { "Platform $platformId reserving seats for session ${request.sessionId}" }

        // Verify platform is active
        val platform = platformRepository.findById(platformId)
            .orElseThrow { VenuesException.ResourceNotFound("Platform not found") }

        if (!platform.isActive()) {
            throw VenuesException.ValidationFailure("Platform is not active")
        }

        // Validate request
        if (request.seatIdentifiers.isNullOrEmpty() && request.gaReservations.isNullOrEmpty()) {
            throw VenuesException.ValidationFailure("Must specify either seats or GA tickets")
        }

        // Create a reservation token for this platform reservation
        val reservationToken = UUID.randomUUID()

        val reservedSeats = mutableListOf<ReservedSeatInfo>()
        val reservedGA = mutableListOf<ReservedGAInfo>()
        var expiresAt: String? = null

        // Reserve individual seats
        request.seatIdentifiers?.forEach { seatIdentifier ->
            val cartRequest = AddSeatToCartRequest(
                sessionId = request.sessionId,
                seatIdentifier = seatIdentifier
            )
            val result = cartService.addSeatToCart(cartRequest, reservationToken)
            expiresAt = result.expiresAt

            // TODO: Get seat details for response
            reservedSeats.add(
                ReservedSeatInfo(
                    seatIdentifier = seatIdentifier,
                    levelName = "TODO",
                    seatNumber = null,
                    rowLabel = null
                )
            )
        }

        // Reserve GA tickets
        request.gaReservations?.forEach { ga ->
            val cartRequest = AddGAToCartRequest(
                sessionId = request.sessionId,
                levelIdentifier = ga.levelIdentifier,
                quantity = ga.quantity
            )
            val result = cartService.addGAToCart(cartRequest, reservationToken)
            expiresAt = result.expiresAt

            reservedGA.add(
                ReservedGAInfo(
                    levelIdentifier = ga.levelIdentifier,
                    levelName = "TODO",
                    quantity = ga.quantity
                )
            )
        }

        logger.info { "Platform ${platform.name} reserved ${reservedSeats.size} seats and ${reservedGA.size} GA tickets" }

        return PlatformReservationResponse(
            reservationToken = reservationToken,
            message = "Reservation successful",
            expiresAt = expiresAt ?: "",
            seats = reservedSeats.takeIf { it.isNotEmpty() },
            gaTickets = reservedGA.takeIf { it.isNotEmpty() }
        )
    }

    /**
     * Release seats for external platform
     */
    fun releaseSeats(
        platformId: Long,
        request: PlatformReleaseRequest
    ): PlatformReleaseResponse {
        logger.debug { "Platform $platformId releasing reservation ${request.reservationToken}" }

        // Verify platform is active
        val platform = platformRepository.findById(platformId)
            .orElseThrow { VenuesException.ResourceNotFound("Platform not found") }

        if (!platform.isActive()) {
            throw VenuesException.ValidationFailure("Platform is not active")
        }

        // Clear the cart by token
        cartService.clearCart(request.reservationToken)

        logger.info { "Platform ${platform.name} released reservation ${request.reservationToken}" }

        return PlatformReleaseResponse(
            message = "Reservation released successfully",
            releasedSeats = 0, // TODO: Track actual counts
            releasedGATickets = 0
        )
    }

    /**
     * Sell seats (convert reservation to booking) for external platform
     */
    fun sellSeats(
        platformId: Long,
        request: PlatformSellRequest
    ): PlatformSellResponse {
        logger.debug { "Platform $platformId selling reservation ${request.reservationToken}" }

        // Verify platform is active
        val platform = platformRepository.findById(platformId)
            .orElseThrow { VenuesException.ResourceNotFound("Platform not found") }

        if (!platform.isActive()) {
            throw VenuesException.ValidationFailure("Platform is not active")
        }

        // TODO: Implement actual booking creation
        // This requires BookingService from booking module
        // For now, return placeholder response

        throw VenuesException.ValidationFailure("Sell functionality not yet implemented - requires booking service integration")

        /*
        // Future implementation:
        val booking = bookingService.createBookingFromCart(
            reservationToken = request.reservationToken,
            paymentMethod = request.paymentMethod,
            paymentReference = request.paymentReference,
            guestEmail = request.guestEmail,
            guestName = request.guestName,
            guestPhone = request.guestPhone,
            externalReference = request.externalReference
        )

        return PlatformSellResponse(
            bookingId = booking.id,
            bookingReference = booking.reference,
            message = "Booking confirmed successfully",
            totalAmount = booking.totalAmount.toString(),
            currency = booking.currency,
            seats = ...,
            gaTickets = ...
        )
        */
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
            id = platform.id!!,
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
            id = platform.id!!,
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

