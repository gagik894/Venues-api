package app.venues.platform.service

import app.venues.booking.api.dto.AddGAToCartRequest
import app.venues.booking.api.dto.AddSeatToCartRequest
import app.venues.booking.service.BookingService
import app.venues.booking.service.CartService
import app.venues.common.exception.VenuesException
import app.venues.platform.api.dto.*
import app.venues.platform.domain.Platform
import app.venues.platform.domain.PlatformStatus
import app.venues.platform.repository.PlatformRepository
import app.venues.platform.repository.WebhookEventRepository
import app.venues.seating.api.SeatingApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.util.*

/**
 * Service for platform management and API operations.
 *
 * Uses SeatingApi for cross-module communication (Hexagonal Architecture).
 */
@Service
@Transactional
class PlatformService(
    private val platformRepository: PlatformRepository,
    private val webhookEventRepository: WebhookEventRepository,
    private val cartService: CartService,
    private val bookingService: BookingService,
    private val seatingApi: SeatingApi
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
        var expiresAt: String? = null

        // Reserve individual seats
        request.seatIdentifiers?.forEach { seatIdentifier ->
            val cartRequest = AddSeatToCartRequest(
                sessionId = request.sessionId,
                seatIdentifier = seatIdentifier
            )
            val result = cartService.addSeatToCart(cartRequest, reservationToken)
            expiresAt = result.expiresAt
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
        }

        // Get actual cart details after all reservations are made
        val cartSummary = cartService.getCartSummary(reservationToken)

        logger.info { "Platform ${platform.name} reserved ${cartSummary.seats.size} seats and ${cartSummary.gaItems.size} GA tickets" }


        // Map cart seats to response with actual details
        val actualSeats = cartSummary.seats.map { seat ->
            ReservedSeatInfo(
                seatIdentifier = seat.seatIdentifier,
                levelName = seat.levelName,
                seatNumber = seat.seatNumber,
                rowLabel = seat.rowLabel
            )
        }

        // Map GA items to response with actual details
        val actualGA = cartSummary.gaItems.map { ga ->
            ReservedGAInfo(
                levelIdentifier = ga.levelIdentifier ?: "",
                levelName = ga.levelName,
                quantity = ga.quantity
            )
        }

        return PlatformReservationResponse(
            reservationToken = reservationToken,
            message = "Reservation successful",
            expiresAt = expiresAt ?: "",
            seats = actualSeats.takeIf { it.isNotEmpty() },
            gaTickets = actualGA.takeIf { it.isNotEmpty() }
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

        // Get cart summary before clearing to track counts
        val cartSummary = try {
            cartService.getCartSummary(request.reservationToken)
        } catch (e: VenuesException) {
            // Cart not found or already expired
            return PlatformReleaseResponse(
                message = "Reservation not found or already expired",
                releasedSeats = 0,
                releasedGATickets = 0
            )
        }

        val seatCount = cartSummary.seats.size
        val gaCount = cartSummary.gaItems.sumOf { it.quantity }

        // Clear the cart by token
        cartService.clearCart(request.reservationToken)

        logger.info { "Platform ${platform.name} released $seatCount seats and $gaCount GA tickets" }

        return PlatformReleaseResponse(
            message = "Reservation released successfully",
            releasedSeats = seatCount,
            releasedGATickets = gaCount
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

        // Create booking from cart
        val booking = bookingService.createBookingFromCart(
            reservationToken = request.reservationToken,
            platformId = platformId,
            paymentMethod = request.paymentMethod,
            paymentReference = request.paymentReference,
            guestEmail = request.guestEmail,
            guestName = request.guestName,
            guestPhone = request.guestPhone
        )

        logger.info { "Platform ${platform.name} completed sale: bookingId=${booking.id}, total=${booking.totalPrice}" }

        // Batch fetch seat and level information via SeatingApi (Hexagonal Architecture)
        val seatIds = booking.items.mapNotNull { it.seatId }.toSet()
        val levelIds = booking.items.mapNotNull { it.levelId }.toSet()

        val seatInfoMap = seatIds.associateWith { seatId ->
            seatingApi.getSeatInfo(seatId)
        }

        val levelInfoMap = levelIds.associateWith { levelId ->
            seatingApi.getLevelInfo(levelId)
        }

        // Map booking items to response with fetched data
        val seats = booking.items
            .filter { it.seatId != null }
            .mapNotNull { item ->
                val seatInfo = seatInfoMap[item.seatId]
                if (seatInfo != null) {
                    ReservedSeatInfo(
                        seatIdentifier = seatInfo.seatIdentifier,
                        levelName = seatInfo.levelName,
                        seatNumber = seatInfo.seatNumber,
                        rowLabel = seatInfo.rowLabel
                    )
                } else null
            }

        val gaTickets = booking.items
            .filter { it.levelId != null }
            .mapNotNull { item ->
                val levelInfo = levelInfoMap[item.levelId]
                if (levelInfo != null) {
                    ReservedGAInfo(
                        levelIdentifier = levelInfo.levelIdentifier ?: item.levelId.toString(),
                        levelName = levelInfo.levelName,
                        quantity = item.quantity
                    )
                } else null
            }

        return PlatformSellResponse(
            bookingId = booking.id.toString(),
            bookingReference = booking.id.toString(), // Can be enhanced with human-readable reference
            message = "Booking confirmed successfully",
            totalAmount = booking.totalPrice.toString(),
            currency = booking.currency,
            seats = seats.takeIf { it.isNotEmpty() },
            gaTickets = gaTickets.takeIf { it.isNotEmpty() }
        )
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

