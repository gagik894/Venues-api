package app.venues.platform.service

import app.venues.booking.api.BookingApi
import app.venues.booking.api.CartApi
import app.venues.booking.api.CartQueryApi
import app.venues.booking.api.dto.AddGAToCartRequest
import app.venues.booking.api.dto.AddSeatToCartRequest
import app.venues.common.exception.VenuesException
import app.venues.platform.api.dto.*
import app.venues.platform.domain.Platform
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
 * This service is a "Consumer" of the `BookingApi`, `CartApi`,
 * and `SeatingApi` Ports, following Hexagonal Architecture.
 */
@Service
@Transactional
class PlatformService(
    private val platformRepository: PlatformRepository,
    private val webhookEventRepository: WebhookEventRepository,
    private val cartApi: CartApi,
    private val cartQueryApi: CartQueryApi,
    private val bookingApi: BookingApi,
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
    // PLATFORM API OPERATIONS (External Platforms)
    // ===========================================

    /**
     * Reserve seats for external platform
     */
    fun reserveSeats(
        platformId: UUID,
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

        val reservationToken = UUID.randomUUID()
        var expiresAt: String? = null

        request.seatIdentifiers?.forEach { seatIdentifier ->
            val cartRequest = AddSeatToCartRequest(
                sessionId = request.sessionId,
                code = seatIdentifier
            )
            val result = cartApi.addSeatToCart(cartRequest, reservationToken)
            expiresAt = result.expiresAt
        }

        request.gaReservations?.forEach { ga ->
            val cartRequest = AddGAToCartRequest(
                sessionId = request.sessionId,
                code = ga.levelIdentifier,
                quantity = ga.quantity
            )
            val result = cartApi.addGAToCart(cartRequest, reservationToken)
            expiresAt = result.expiresAt
        }

        val cartSummary = cartQueryApi.getCartSummary(reservationToken)
        logger.info { "Platform ${platform.name} reserved ${cartSummary.seats.size} seats and ${cartSummary.gaItems.size} GA items" }


        // Map cart seats to response with actual details
        val actualSeats = cartSummary.seats.map { seat ->
            ReservedSeatInfo(
                seatIdentifier = seat.code,
                levelName = seat.levelName,
                seatNumber = seat.number,
                rowLabel = seat.rowLabel
            )
        }

        // Map GA items to response with actual details
        val actualGA = cartSummary.gaItems.map { ga ->
            ReservedGAInfo(
                levelIdentifier = ga.code ?: "",
                levelName = ga.name,
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
        platformId: UUID,
        request: PlatformReleaseRequest
    ): PlatformReleaseResponse {
        logger.debug { "Platform $platformId releasing reservation ${request.reservationToken}" }

        val platform = platformRepository.findById(platformId)
            .orElseThrow { VenuesException.ResourceNotFound("Platform not found") }

        if (!platform.isActive()) {
            throw VenuesException.ValidationFailure("Platform is not active")
        }

        val cartSummary = try {
            cartQueryApi.getCartSummary(request.reservationToken)
        } catch (e: VenuesException) {
            return PlatformReleaseResponse(
                message = "Reservation not found or already expired",
                releasedSeats = 0,
                releasedGATickets = 0
            )
        }

        val seatCount = cartSummary.seats.size
        val gaCount = cartSummary.gaItems.sumOf { it.quantity }

        cartApi.clearCart(request.reservationToken)
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
        // TODO: Implement sellSeats logic
//        val platform = platformRepository.findById(platformId)
//            .orElseThrow { VenuesException.ResourceNotFound("Platform not found") }
//
//        if (!platform.isActive()) {
//            throw VenuesException.ValidationFailure("Platform is not active")
//        }
//
//        // 1. Create the API Port DTO
//        val createBookingRequest = CreateBookingRequest(
//            cartToken = request.reservationToken,
//            platformId = platformId,
//            customer = CustomerDetailsDto(
//                guestName = request.guestName ?: "Guest",
//                guestEmail = request.guestEmail ?: "noemail@empty.com",
//                guestPhone = request.guestPhone
//            ),
//            payment = PlatformPaymentDto(
//                paymentMethod = request.paymentMethod,
//                paymentReference = request.paymentReference
//            )
//        )
//
//        // 2. Call the BookingApi Port
//        val bookingDto: BookingDto = bookingApi.createBookingFromCart(createBookingRequest)
//
//        logger.info { "Platform ${platform.name} completed sale: bookingId=${bookingDto.id}, total=${bookingDto.totalPrice}" }
//
//        // 3. Map the internal DTO to the external PlatformSellResponse
//        val seats = bookingDto.items
//            .filter { it.type == "SEAT" }
//            .map { mapBookingItemToReservedSeat(it) }
//
//        val gaTickets = bookingDto.items
//            .filter { it.type == "GA" || it.type == "TABLE" }
//            .map { mapBookingItemToReservedGA(it) }
//
//        return PlatformSellResponse(
//            bookingId = bookingDto.id.toString(),
//            bookingReference = bookingDto.id.toString(),
//            message = "Booking confirmed successfully",
//            totalAmount = bookingDto.totalPrice.toString(),
//            currency = bookingDto.currency,
//            seats = seats.takeIf { it.isNotEmpty() },
//            gaTickets = gaTickets.takeIf { it.isNotEmpty() }
//        )
        return TODO()
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