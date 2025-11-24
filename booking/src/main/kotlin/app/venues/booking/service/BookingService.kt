package app.venues.booking.service

import app.venues.booking.api.BookingApi
import app.venues.booking.api.domain.BookingStatus
import app.venues.booking.api.dto.*
import app.venues.booking.api.mapper.BookingItemData
import app.venues.booking.api.mapper.BookingMapper
import app.venues.booking.domain.Booking
import app.venues.booking.repository.BookingRepository
import app.venues.booking.repository.CartRepository
import app.venues.booking.service.model.BookingCreationContext
import app.venues.booking.service.model.CartSnapshot
import app.venues.common.exception.VenuesException
import app.venues.event.api.EventApi
import app.venues.seating.api.SeatingApi
import app.venues.user.api.UserApi
import app.venues.venue.api.VenueApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Service for booking management operations.
 *
 * Uses API interfaces (UserApi, VenueApi, SeatingApi) for cross-module communication,
 * enforcing Hexagonal Architecture boundaries.
 *
 * Handles checkout, confirmation, cancellation, and booking retrieval.
 */
@Service
@Transactional
class BookingService(
    private val bookingRepository: BookingRepository,
    private val cartRepository: CartRepository,
    private val guestService: GuestService,
    private val bookingCreationService: BookingCreationService,
    private val bookingMapper: BookingMapper,
    private val userApi: UserApi,
    private val seatingApi: SeatingApi,
    private val eventApi: EventApi,
    private val venueApi: VenueApi
) : BookingApi {
    private val logger = KotlinLogging.logger {}

    // ===========================================
    // CHECKOUT
    // ===========================================

    /**
     * Convert cart to booking (checkout).
     */
    fun checkout(request: CheckoutRequest, userId: UUID? = null): CheckoutResponse {
        logger.debug { "Processing checkout: token=${request.token}, email=${request.email}" }

        // Validate cart and get session
        val cartToken = request.token ?: throw VenuesException.ValidationFailure("Cart token is required for checkout")
        val cartSnapshot = validateCartAndGetSession(cartToken)

        // Determine user or guest
        val guest = if (userId == null) {
            guestService.findOrCreateGuest(request.email, request.name, request.phone)
        } else null

        val creationResult = bookingCreationService.assembleBooking(
            snapshot = cartSnapshot,
            context = BookingCreationContext(
                userId = userId,
                guest = guest,
                platformId = null,
                paymentReference = null
            )
        )

        // Save booking
        val savedBooking = bookingRepository.save(creationResult.booking)

        redeemPromoIfNeeded(savedBooking)

        // Delete cart (CASCADE deletes items)
        deleteCart(cartToken)

        logger.info {
            "Checkout completed: bookingId=${savedBooking.id}, total=${creationResult.pricing.total}, " +
                    "items=${savedBooking.items.size}"
        }

        // Prepare response with fetched data
        val bookingResponse = prepareBookingResponse(savedBooking)

        return CheckoutResponse(
            booking = bookingResponse,
            message = "Booking created successfully. Please complete payment to confirm."
        )
    }

    // ===========================================
    // BOOKING MANAGEMENT
    // ===========================================

    /**
     * Confirm booking payment.
     */
    fun confirmBooking(bookingId: UUID, request: ConfirmBookingRequest, userId: UUID? = null): BookingResponse {
        logger.debug { "Confirming booking: $bookingId" }

        val booking = bookingRepository.findById(bookingId)
            .orElseThrow { VenuesException.ResourceNotFound("Booking not found") }

        // Verify ownership if user is authenticated
        if (userId != null && booking.userId != userId) {
            throw VenuesException.AuthorizationFailure("You can only confirm your own bookings")
        }

        if (booking.status == BookingStatus.CONFIRMED) {
            return prepareBookingResponse(booking)
        }

        booking.confirm(request.paymentId)
        val savedBooking = bookingRepository.save(booking)

        // Finalize inventory (RESERVED -> SOLD)
        finalizeBookingInventory(booking)

        logger.info { "Booking confirmed: $bookingId" }

        return prepareBookingResponse(savedBooking)
    }

    /**
     * Cancel booking.
     */
    fun cancelBooking(bookingId: UUID, request: CancelBookingRequest, userId: UUID? = null): BookingResponse {
        logger.debug { "Cancelling booking: $bookingId" }

        val booking = bookingRepository.findById(bookingId)
            .orElseThrow { VenuesException.ResourceNotFound("Booking not found") }

        // Verify ownership if user is authenticated
        if (userId != null && booking.userId != userId) {
            throw VenuesException.AuthorizationFailure("You can only cancel your own bookings")
        }

        if (!booking.isCancellable()) {
            throw VenuesException.ValidationFailure("Booking cannot be cancelled (status: ${booking.status})")
        }

        booking.cancel(request.reason)
        val savedBooking = bookingRepository.save(booking)

        releasePromoIfNeeded(savedBooking)
        // Release inventory (RESERVED/SOLD -> AVAILABLE)
        releaseBookingInventory(booking)

        logger.info { "Booking cancelled: $bookingId" }

        return prepareBookingResponse(savedBooking)
    }

    /**
     * Expire a booking (system action).
     * Called by cleanup job for PENDING bookings that timed out.
     */
    fun expireBooking(bookingId: UUID) {
        logger.info { "Expiring booking: $bookingId" }
        val booking = bookingRepository.findById(bookingId)
            .orElseThrow { VenuesException.ResourceNotFound("Booking not found") }

        if (booking.status != BookingStatus.PENDING) {
            return
        }

        booking.cancel("Booking expired")
        val savedBooking = bookingRepository.save(booking)

        releasePromoIfNeeded(savedBooking)
        releaseBookingInventory(savedBooking)
    }

    /**
     * Get booking by ID (Internal API).
     */
    @Transactional(readOnly = true)
    override fun getBookingById(bookingId: UUID): BookingResponse {
        return getBookingById(bookingId, null)
    }

    /**
     * Get booking by ID (Controller).
     */
    @Transactional(readOnly = true)
    fun getBookingById(bookingId: UUID, userId: UUID? = null): BookingResponse {
        logger.debug { "Fetching booking: $bookingId" }

        val booking = bookingRepository.findById(bookingId)
            .orElseThrow { VenuesException.ResourceNotFound("Booking not found") }

        // Verify ownership if user is authenticated
        if (userId != null && booking.userId != userId) {
            throw VenuesException.AuthorizationFailure("You can only view your own bookings")
        }

        return prepareBookingResponse(booking)
    }

    /**
     * Get user's bookings.
     */
    @Transactional(readOnly = true)
    fun getUserBookings(userId: UUID, pageable: Pageable): Page<BookingResponse> {
        logger.debug { "Fetching bookings for user: $userId" }

        return bookingRepository.findByUserId(userId, pageable)
            .map { prepareBookingResponse(it) }
    }

    /**
     * Get guest's bookings by email.
     */
    @Transactional(readOnly = true)
    fun getGuestBookings(email: String, pageable: Pageable): Page<BookingResponse> {
        logger.debug { "Fetching bookings for guest: $email" }

        val guest = guestService.findGuestByEmail(email)
            ?: throw VenuesException.ResourceNotFound("No bookings found for this email")

        return bookingRepository.findByGuestId(guest.id, pageable)
            .map { prepareBookingResponse(it) }
    }

    // ===========================================
    // RESPONSE PREPARATION
    // ===========================================

    /**
     * Prepare booking response with enriched data from other modules.
     */
    private fun prepareBookingResponse(booking: Booking): BookingResponse {
        // Fetch session from event module
        val sessionDto = eventApi.getEventSessionInfo(booking.sessionId)
            ?: throw VenuesException.ResourceNotFound("Event session not found")

        // Get customer info using UserApi (Hexagonal Architecture)
        val customerEmail = booking.userId?.let {
            userApi.getUserEmail(it) ?: ""
        } ?: booking.guest?.email ?: ""

        val customerName = booking.userId?.let {
            userApi.getUserFullName(it) ?: ""
        } ?: booking.guest?.name ?: ""

        // Prepare items data using SeatingApi (Hexagonal Architecture)
        val itemsData = booking.items.map { item ->
            val seatId = item.seatId
            val levelId = item.gaAreaId

            val seatIdentifier = seatId?.let {
                seatingApi.getSeatInfo(it)?.code
            }
            val levelName = when {
                seatId != null -> seatingApi.getSeatInfo(seatId)?.zoneName
                levelId != null -> seatingApi.getGaInfo(levelId)?.name
                else -> null
            }

            BookingItemData(
                id = item.id ?: error("Booking item ID cannot be null"),
                seatId = seatId,
                seatIdentifier = seatIdentifier,
                levelId = levelId,
                levelName = levelName,
                tableId = item.tableId,
                quantity = item.quantity,
                unitPrice = item.unitPrice.toString(),
                totalPrice = item.getTotalPrice().toString(),
                priceTemplateName = item.priceTemplateName
            )
        }


        return bookingMapper.toResponse(
            booking = booking,
            eventTitle = sessionDto.eventTitle,
            eventDescription = sessionDto.eventDescription,
            sessionStartTime = sessionDto.startTime.toString(),
            sessionEndTime = sessionDto.endTime.toString(),
            customerEmail = customerEmail,
            customerName = customerName,
            itemsData = itemsData
        )
    }

    // ===========================================
    // PRIVATE HELPER METHODS (DRY PRINCIPLE)
    // ===========================================

    /**
     * Validates cart and fetches session data for checkout.
     * Uses @EntityGraph optimization to load all items in a single query.
     *
     * @param cartToken Cart token
     * @return CartSnapshot with validated cart items and session info
     * @throws VenuesException.ResourceNotFound if cart not found
     * @throws VenuesException.ValidationFailure if cart is expired
     */
    private fun validateCartAndGetSession(cartToken: UUID): CartSnapshot {
        // Get cart with ALL items in single query (via @EntityGraph)
        val cart = cartRepository.findWithItemsByToken(cartToken)
            ?: throw VenuesException.ResourceNotFound("Cart not found")

        // Check if cart expired
        if (cart.isExpired()) {
            throw VenuesException.ValidationFailure("Cart has expired")
        }

        // Collections are already loaded - no additional queries
        val cartSeats = cart.seats.toList()
        val cartItems = cart.gaItems.toList()
        val cartTables = cart.tables.toList()

        if (cartSeats.isEmpty() && cartItems.isEmpty() && cartTables.isEmpty()) {
            throw VenuesException.ResourceNotFound("Cart is empty")
        }

        // Fetch session from event module
        val sessionDto = eventApi.getEventSessionInfo(cart.sessionId)
            ?: throw VenuesException.ResourceNotFound("Event session not found")

        return CartSnapshot(
            cart = cart,
            seats = cartSeats,
            gaItems = cartItems,
            tables = cartTables,
            session = sessionDto
        )
    }


    /**
     * Delete cart by token.
     * CASCADE deletes all cart items automatically.
     *
     * @param cartToken Cart token
     */
    private fun deleteCart(cartToken: UUID) {
        val cart = cartRepository.findByToken(cartToken)
        if (cart != null) {
            cartRepository.delete(cart)
            logger.debug { "Deleted cart after checkout: $cartToken" }
        }
    }

    // ===========================================
    // PLATFORM INTEGRATION
    // ===========================================

    /**
     * Create booking from cart for platform integration.
     * Used when external platforms complete payment and need to finalize booking.
     */
    private fun createPlatformBooking(
        cartToken: UUID,
        platformId: UUID,
        paymentReference: String?,
        guestEmail: String? = null,
        guestName: String? = null,
        guestPhone: String? = null
    ): Booking {
        logger.debug { "Creating booking from cart for platform $platformId: token=$cartToken" }

        // Validate cart and get session
        val cartSnapshot = validateCartAndGetSession(cartToken)

        // Create or find guest if email provided
        val guest = if (guestEmail != null && guestName != null) {
            guestService.findOrCreateGuest(guestEmail, guestName, guestPhone ?: "")
        } else null

        val creationResult = bookingCreationService.assembleBooking(
            snapshot = cartSnapshot,
            context = BookingCreationContext(
                userId = null,
                guest = guest,
                platformId = platformId,
                paymentReference = paymentReference
            )
        )
        val booking = creationResult.booking

        // Confirm immediately (payment already done by platform)
        // External platforms do not provide internal payment UUIDs, so paymentId is null.
        booking.confirm(null)

        // Save booking
        val savedBooking = bookingRepository.save(booking)

        redeemPromoIfNeeded(savedBooking)

        // Finalize inventory (RESERVED -> SOLD)
        finalizeBookingInventory(booking)

        // Delete cart (CASCADE deletes items)
        deleteCart(cartToken)

        logger.info {
            "Booking created from platform $platformId: bookingId=${savedBooking.id}, " +
                    "total=${creationResult.pricing.total}"
        }

        return savedBooking
    }

    /**
     * Creates a new booking from an existing cart token.
     * This is the primary method for converting a cart into a sale.
     */
    override fun createBookingFromCart(
        cartToken: UUID,
        platformId: UUID,
        paymentMethod: String,
        paymentReference: String?,
        guestEmail: String,
        guestName: String,
        guestPhone: String?
    ): BookingResponse {
        val booking = createPlatformBooking(
            cartToken = cartToken,
            platformId = platformId,
            paymentReference = paymentReference,
            guestEmail = guestEmail,
            guestName = guestName,
            guestPhone = guestPhone
        )
        return prepareBookingResponse(booking)
    }

    /**
     * Confirms a booking after successful payment.
     * This should finalize the sale and generate tickets.
     */
    override fun confirmBooking(bookingId: UUID, paymentId: UUID) {
        logger.info { "Confirming booking via internal API: $bookingId, payment: $paymentId" }
        val booking = bookingRepository.findById(bookingId)
            .orElseThrow { VenuesException.ResourceNotFound("Booking not found") }

        if (booking.status == BookingStatus.CONFIRMED) {
            logger.warn { "Booking $bookingId is already confirmed" }
            return
        }

        booking.confirm(paymentId)
        val savedBooking = bookingRepository.save(booking)

        finalizeBookingInventory(savedBooking)
    }

    /**
     * Cancels a booking after failed payment.
     * This should release held seats.
     */
    override fun cancelBooking(bookingId: UUID) {
        logger.info { "Cancelling booking via internal API: $bookingId" }
        val booking = bookingRepository.findById(bookingId)
            .orElseThrow { VenuesException.ResourceNotFound("Booking not found") }

        if (!booking.isCancellable()) {
            logger.warn { "Booking $bookingId cannot be cancelled (status: ${booking.status})" }
            return
        }

        booking.cancel("Payment failed or cancelled")
        val savedBooking = bookingRepository.save(booking)

        releasePromoIfNeeded(savedBooking)
        releaseBookingInventory(savedBooking)
    }

    /**
     * Refunds a booking and updates its status.
     */
    override fun refundBooking(bookingId: UUID, reason: String?) {
        logger.info { "Refunding booking via internal API: $bookingId" }
        val booking = bookingRepository.findById(bookingId)
            .orElseThrow { VenuesException.ResourceNotFound("Booking not found") }

        if (booking.status != BookingStatus.CONFIRMED) {
            logger.warn { "Booking $bookingId cannot be refunded (status: ${booking.status})" }
            return
        }
        booking.cancel(reason ?: "Booking refunded")
        val savedBooking = bookingRepository.save(booking)
        releasePromoIfNeeded(savedBooking)
        releaseBookingInventory(savedBooking)
    }

    private fun redeemPromoIfNeeded(booking: Booking) {
        val code = booking.promoCode
        val venueId = booking.venueId
        if (code != null && venueId != null) {
            venueApi.redeemPromoCode(venueId, code)
            logger.debug { "Redeemed promo code $code for booking ${booking.id}" }
        }
    }

    private fun releasePromoIfNeeded(booking: Booking) {
        val code = booking.promoCode
        val venueId = booking.venueId
        if (code != null && venueId != null) {
            try {
                venueApi.releasePromoCode(venueId, code)
                logger.debug { "Released promo code $code for booking ${booking.id}" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to release promo code $code for booking ${booking.id}" }
            }
        }
    }

    /**
     * Finalize inventory (RESERVED -> SOLD).
     */
    private fun finalizeBookingInventory(booking: Booking) {
        // 1. Finalize Seats
        val seatIds = booking.items.mapNotNull { it.seatId }
        if (seatIds.isNotEmpty()) {
            eventApi.sellSeatsBatch(booking.sessionId, seatIds)
        }

        // 2. Finalize GA
        val gaItems = booking.items.filter { it.gaAreaId != null }
        if (gaItems.isNotEmpty()) {
            val gaQuantities = gaItems.associate { it.gaAreaId!! to it.quantity }
            eventApi.sellGaBatch(booking.sessionId, gaQuantities)
        }

        // 3. Finalize Tables
        val tableIds = booking.items.mapNotNull { it.tableId }
        if (tableIds.isNotEmpty()) {
            eventApi.sellTablesBatch(booking.sessionId, tableIds)
        }
    }

    /**
     * Release inventory (RESERVED/SOLD -> AVAILABLE).
     */
    private fun releaseBookingInventory(booking: Booking) {
        // 1. Release Seats
        val seatIds = booking.items.mapNotNull { it.seatId }
        if (seatIds.isNotEmpty()) {
            eventApi.releaseSeatsBatch(booking.sessionId, seatIds)
        }

        // 2. Release GA
        val gaItems = booking.items.filter { it.gaAreaId != null }
        if (gaItems.isNotEmpty()) {
            val gaQuantities = gaItems.associate { it.gaAreaId!! to it.quantity }
            eventApi.releaseGaBatch(booking.sessionId, gaQuantities)
        }

        // 3. Release Tables
        val tableIds = booking.items.mapNotNull { it.tableId }
        if (tableIds.isNotEmpty()) {
            eventApi.releaseTablesBatch(booking.sessionId, tableIds)
        }
    }
}
