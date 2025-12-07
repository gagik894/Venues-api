package app.venues.platform.service

import app.venues.booking.api.BookingApi
import app.venues.booking.api.CartApi
import app.venues.booking.api.CartQueryApi
import app.venues.booking.api.dto.AddGAToCartRequest
import app.venues.booking.api.dto.AddSeatToCartRequest
import app.venues.booking.api.dto.AddTableToCartRequest
import app.venues.booking.api.dto.UpdateGAQuantityRequest
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
    private val seatingApi: SeatingApi,
    private val rateLimitService: PlatformRateLimitService,
    private val idempotencyService: PlatformIdempotencyService
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
        request: PlatformReservationRequest,
        idempotencyKey: String?
    ): PlatformReservationResponse {
        logger.debug { "Platform $platformId reserving seats for session ${request.sessionId}" }

        // Verify platform is active
        val platform = platformRepository.findById(platformId)
            .orElseThrow { VenuesException.ResourceNotFound("Platform not found") }

        if (!platform.isActive()) {
            throw VenuesException.ValidationFailure("Platform is not active")
        }

        rateLimitService.enforce(platformId, platform.rateLimit)

        // Validate request
        if (request.seatIdentifiers.isNullOrEmpty() && request.gaReservations.isNullOrEmpty() && request.tableIdentifiers.isNullOrEmpty()) {
            throw VenuesException.ValidationFailure("Must specify seats, GA tickets, or tables")
        }
        return idempotencyService.withIdempotency(
            idempotencyKey = idempotencyKey,
            platformId = platformId,
            endpoint = "reserve",
            responseType = PlatformReservationResponse::class.java
        ) {
            val requestedGaByIdentifier = request.gaReservations.orEmpty().associateBy { it.levelIdentifier }
            val reservationToken = request.reservationToken ?: UUID.randomUUID()
            var expiresAt: String? = null

            var existingSeatCodes = emptySet<String>()
            var existingGaQuantities = emptyMap<String, Int>()
            var existingTableCodes = emptySet<String>()

            if (request.reservationToken != null) {
                try {
                    val existingSummary = cartQueryApi.getCartSummary(reservationToken)
                    if (existingSummary.sessionId != request.sessionId) {
                        throw VenuesException.ValidationFailure("Reservation token belongs to a different session")
                    }

                    expiresAt = existingSummary.expiresAt
                    existingSeatCodes = existingSummary.seats.map { it.code }.toSet()
                    existingGaQuantities = existingSummary.gaItems.associate { gaItem ->
                        val key = gaItem.code ?: gaItem.name
                        key to gaItem.quantity
                    }
                    existingTableCodes = existingSummary.tables.map { it.code }.toSet()
                } catch (ignored: VenuesException) {
                    // Treat as new reservation token if cart not found/expired
                }
            }

            request.seatIdentifiers?.forEach { seatIdentifier ->
                if (existingSeatCodes.contains(seatIdentifier)) {
                    return@forEach
                }
                val cartRequest = AddSeatToCartRequest(
                    sessionId = request.sessionId,
                    code = seatIdentifier
                )
                val result = cartApi.addSeatToCart(cartRequest, reservationToken)
                expiresAt = result.expiresAt
            }

            request.gaReservations?.forEach { ga ->
                val existingQuantity = existingGaQuantities[ga.levelIdentifier]
                if (existingQuantity != null) {
                    if (existingQuantity == ga.quantity) {
                        return@forEach
                    }
                    val result = cartApi.updateGAQuantity(
                        token = reservationToken,
                        levelIdentifier = ga.levelIdentifier,
                        request = UpdateGAQuantityRequest(quantity = ga.quantity)
                    )
                    expiresAt = result.expiresAt
                    return@forEach
                }

                val cartRequest = AddGAToCartRequest(
                    sessionId = request.sessionId,
                    code = ga.levelIdentifier,
                    quantity = ga.quantity
                )
                val result = cartApi.addGAToCart(cartRequest, reservationToken)
                expiresAt = result.expiresAt
            }

            request.tableIdentifiers?.forEach { tableIdentifier ->
                if (existingTableCodes.contains(tableIdentifier)) {
                    return@forEach
                }
                val cartRequest = AddTableToCartRequest(
                    sessionId = request.sessionId,
                    code = tableIdentifier
                )
                val result = cartApi.addTableToCart(cartRequest, reservationToken)
                expiresAt = result.expiresAt
            }

            val cartSummary = cartQueryApi.getCartSummary(reservationToken)
            logger.info {
                "Platform ${platform.name} reserved ${cartSummary.seats.size} seats, ${cartSummary.gaItems.sumOf { it.quantity }} GA tickets, ${cartSummary.tables.size} tables"
            }

            val actualSeats = cartSummary.seats.map { seat ->
                ReservedSeatInfo(
                    seatIdentifier = seat.code,
                    levelName = seat.levelName,
                    seatNumber = seat.number,
                    rowLabel = seat.rowLabel
                )
            }

            val actualGA = cartSummary.gaItems.map { ga ->
                val levelIdentifier = ga.code
                    ?: requestedGaByIdentifier.keys.firstOrNull { it.equals(ga.name, ignoreCase = true) }
                    ?: ga.name
                ReservedGAInfo(
                    levelIdentifier = levelIdentifier,
                    levelName = ga.name,
                    quantity = ga.quantity
                )
            }

            val actualTables = cartSummary.tables.map { table ->
                ReservedTableInfo(
                    tableIdentifier = table.code,
                    tableName = table.number
                )
            }

            PlatformReservationResponse(
                reservationToken = reservationToken,
                message = "Reservation successful",
                expiresAt = expiresAt ?: "",
                seats = actualSeats.takeIf { it.isNotEmpty() },
                gaTickets = actualGA.takeIf { it.isNotEmpty() },
                tables = actualTables.takeIf { it.isNotEmpty() }
            )
        }
    }

    /**
     * Release seats for external platform
     */
    fun releaseSeats(
        platformId: UUID,
        request: PlatformReleaseRequest,
        idempotencyKey: String?
    ): PlatformReleaseResponse {
        logger.debug { "Platform $platformId releasing reservation ${request.reservationToken}" }

        val platform = platformRepository.findById(platformId)
            .orElseThrow { VenuesException.ResourceNotFound("Platform not found") }

        if (!platform.isActive()) {
            throw VenuesException.ValidationFailure("Platform is not active")
        }

        rateLimitService.enforce(platformId, platform.rateLimit)

        return idempotencyService.withIdempotency(
            idempotencyKey = idempotencyKey,
            platformId = platformId,
            endpoint = "release",
            responseType = PlatformReleaseResponse::class.java
        ) {
            val cartSummary = try {
                cartQueryApi.getCartSummary(request.reservationToken)
            } catch (e: VenuesException) {
                return@withIdempotency PlatformReleaseResponse(
                    message = "Reservation not found or already expired",
                    releasedSeats = 0,
                    releasedGATickets = 0,
                    releasedTables = 0
                )
            }

            val seatCount = cartSummary.seats.size
            val gaCount = cartSummary.gaItems.sumOf { it.quantity }
            val tableCount = cartSummary.tables.size

            cartApi.clearCart(request.reservationToken)
            logger.info { "Platform ${platform.name} released $seatCount seats, $gaCount GA tickets, $tableCount tables" }

            PlatformReleaseResponse(
                message = "Reservation released successfully",
                releasedSeats = seatCount,
                releasedGATickets = gaCount,
                releasedTables = tableCount
            )
        }
    }

    /**
     * Sell seats (convert reservation to booking) for external platform
     */
    fun sellSeats(
        platformId: UUID,
        request: PlatformSellRequest,
        idempotencyKey: String?
    ): PlatformSellResponse {
        logger.debug { "Platform $platformId selling reservation ${request.reservationToken}" }

        val platform = platformRepository.findById(platformId)
            .orElseThrow { VenuesException.ResourceNotFound("Platform not found") }

        if (!platform.isActive()) {
            throw VenuesException.ValidationFailure("Platform is not active")
        }

        rateLimitService.enforce(platformId, platform.rateLimit)

        return idempotencyService.withIdempotency(
            idempotencyKey = idempotencyKey,
            platformId = platformId,
            endpoint = "sell",
            responseType = PlatformSellResponse::class.java
        ) {
            val bookingDto = bookingApi.createBookingFromCart(
                cartToken = request.reservationToken,
                platformId = platformId,
                paymentMethod = request.paymentMethod,
                paymentReference = request.paymentReference,
                guestEmail = request.guestEmail ?: "noemail@empty.com",
                guestName = request.guestName ?: "Guest",
                guestPhone = request.guestPhone
            )

            logger.info { "Platform ${platform.name} completed sale: bookingId=${bookingDto.id}, total=${bookingDto.totalPrice}" }

            val seats = bookingDto.items
                .filter { it.seatId != null }
                .map { item ->
                    ReservedSeatInfo(
                        seatIdentifier = item.seatIdentifier ?: "",
                        levelName = item.levelName ?: "",
                        seatNumber = null,
                        rowLabel = null
                    )
                }

            val gaTickets = bookingDto.items
                .filter { it.levelId != null }
                .map { item ->
                    ReservedGAInfo(
                        levelIdentifier = item.levelName ?: item.levelId.toString(),
                        levelName = item.levelName ?: "",
                        quantity = item.quantity
                    )
                }

            val tables = bookingDto.items
                .filter { it.tableId != null }
                .mapNotNull { item ->
                    val tableId = item.tableId ?: return@mapNotNull null
                    val tableInfo = seatingApi.getTableInfo(tableId)
                    tableInfo?.let {
                        ReservedTableInfo(
                            tableIdentifier = it.code,
                            tableName = it.tableNumber
                        )
                    }
                }

            PlatformSellResponse(
                bookingId = bookingDto.id.toString(),
                bookingReference = bookingDto.id.toString(),
                message = "Booking confirmed successfully",
                totalAmount = bookingDto.totalPrice.toString(),
                seats = seats.takeIf { it.isNotEmpty() },
                gaTickets = gaTickets.takeIf { it.isNotEmpty() },
                tables = tables.takeIf { it.isNotEmpty() }
            )
        }
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
