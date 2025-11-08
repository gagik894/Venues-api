package app.venues.booking.service

import app.venues.booking.api.dto.*
import app.venues.booking.api.mapper.BookingItemData
import app.venues.booking.api.mapper.BookingMapper
import app.venues.booking.domain.Booking
import app.venues.booking.domain.BookingItem
import app.venues.booking.repository.BookingItemRepository
import app.venues.booking.repository.BookingRepository
import app.venues.booking.repository.CartItemRepository
import app.venues.booking.repository.CartSeatRepository
import app.venues.common.exception.VenuesException
import app.venues.event.repository.EventSessionRepository
import app.venues.event.repository.SessionLevelConfigRepository
import app.venues.event.repository.SessionSeatConfigRepository
import app.venues.seating.api.SeatingApi
import app.venues.user.api.UserApi
import app.venues.venue.api.VenueApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
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
    private val bookingItemRepository: BookingItemRepository,
    private val cartSeatRepository: CartSeatRepository,
    private val cartItemRepository: CartItemRepository,
    private val sessionSeatConfigRepository: SessionSeatConfigRepository,
    private val sessionLevelConfigRepository: SessionLevelConfigRepository,
    private val eventSessionRepository: EventSessionRepository,
    private val guestService: GuestService,
    private val bookingMapper: BookingMapper,
    private val userApi: UserApi,
    private val venueApi: VenueApi,
    private val seatingApi: SeatingApi
) {
    private val logger = KotlinLogging.logger {}

    // ===========================================
    // CHECKOUT
    // ===========================================

    /**
     * Convert cart to booking (checkout).
     */
    fun checkout(request: CheckoutRequest, userId: Long? = null): CheckoutResponse {
        logger.debug { "Processing checkout: token=${request.token}, email=${request.email}" }

        // Validate cart and get session
        val cartData = validateCartAndGetSession(request.token)

        // Determine user or guest
        val guest = if (userId == null) {
            guestService.findOrCreateGuest(request.email, request.name, request.phone)
        } else null

        // Create booking with items
        val booking = createBookingFromCartData(
            cartData = cartData,
            userId = userId,
            guest = guest,
            platformId = null,
            reservationToken = request.token
        )

        // Save booking
        val savedBooking = bookingRepository.save(booking)

        // Delete cart items
        deleteCartItems(request.token)

        logger.info { "Checkout completed: bookingId=${savedBooking.id}, total=${booking.totalPrice}" }

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
    fun confirmBooking(bookingId: UUID, request: ConfirmBookingRequest, userId: Long? = null): BookingResponse {
        logger.debug { "Confirming booking: $bookingId" }

        val booking = bookingRepository.findById(bookingId)
            .orElseThrow { VenuesException.ResourceNotFound("Booking not found") }

        // Verify ownership if user is authenticated
        if (userId != null && booking.userId != userId) {
            throw VenuesException.AuthorizationFailure("You can only confirm your own bookings")
        }

        booking.confirm(request.paymentId)
        val savedBooking = bookingRepository.save(booking)

        logger.info { "Booking confirmed: $bookingId" }

        return prepareBookingResponse(savedBooking)
    }

    /**
     * Cancel booking.
     */
    fun cancelBooking(bookingId: UUID, request: CancelBookingRequest, userId: Long? = null): BookingResponse {
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

        logger.info { "Booking cancelled: $bookingId" }

        return prepareBookingResponse(savedBooking)
    }

    /**
     * Get booking by ID.
     */
    @Transactional(readOnly = true)
    fun getBookingById(bookingId: UUID, userId: Long? = null): BookingResponse {
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
    fun getUserBookings(userId: Long, pageable: Pageable): Page<BookingResponse> {
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

        return bookingRepository.findByGuestId(guest.id!!, pageable)
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
        val session = eventSessionRepository.findById(booking.sessionId)
            .orElseThrow { VenuesException.ResourceNotFound("Event session not found") }

        val event = session.event

        // Get customer info using UserApi (Hexagonal Architecture)
        val customerEmail = booking.userId?.let {
            userApi.getUserEmail(it) ?: ""
        } ?: booking.guest?.email ?: ""

        val customerName = booking.userId?.let {
            userApi.getUserFullName(it) ?: ""
        } ?: booking.guest?.name ?: ""

        // Prepare items data using SeatingApi (Hexagonal Architecture)
        val itemsData = booking.items.map { item ->
            val seatIdentifier = item.seatId?.let {
                seatingApi.getSeatInfo(it)?.seatIdentifier
            }
            val levelName = item.levelId?.let {
                seatingApi.getLevelInfo(it)?.levelName
            }

            BookingItemData(
                id = item.id!!,
                seatId = item.seatId,
                seatIdentifier = seatIdentifier,
                levelId = item.levelId,
                levelName = levelName,
                quantity = item.quantity,
                unitPrice = item.unitPrice.toString(),
                totalPrice = item.getTotalPrice().toString(),
                priceTemplateName = item.priceTemplateName
            )
        }

        val venueName = venueApi.getVenueName(event.venueId) ?: "Unknown"

        return bookingMapper.toResponse(
            booking = booking,
            eventTitle = event.title,
            eventDescription = event.description,
            venueName = venueName,
            sessionStartTime = session.startTime.toString(),
            sessionEndTime = session.endTime.toString(),
            customerEmail = customerEmail,
            customerName = customerName,
            itemsData = itemsData
        )
    }

    // ===========================================
    // PRIVATE HELPER METHODS (DRY PRINCIPLE)
    // ===========================================

    /**
     * Data class to hold validated cart data.
     */
    private data class CartData(
        val cartSeats: List<app.venues.booking.domain.CartSeat>,
        val cartItems: List<app.venues.booking.domain.CartItem>,
        val session: app.venues.event.domain.EventSession,
        val event: app.venues.event.domain.Event
    )

    /**
     * Validate cart and get session data.
     *
     * Centralizes cart validation logic to follow DRY principle.
     *
     * @param reservationToken Cart reservation token
     * @return CartData with validated cart items and session info
     * @throws VenuesException.ResourceNotFound if cart is empty
     * @throws VenuesException.ValidationFailure if cart is expired
     */
    private fun validateCartAndGetSession(reservationToken: UUID): CartData {
        // Get cart items
        val cartSeats = cartSeatRepository.findByReservationToken(reservationToken)
        val cartItems = cartItemRepository.findByReservationToken(reservationToken)

        if (cartSeats.isEmpty() && cartItems.isEmpty()) {
            throw VenuesException.ResourceNotFound("Cart not found or already checked out")
        }

        // Verify not expired
        if (cartSeats.any { it.isExpired() } || cartItems.any { it.isExpired() }) {
            throw VenuesException.ValidationFailure("Cart has expired")
        }

        // Get session ID (all items should be from same session)
        val sessionId = cartSeats.firstOrNull()?.sessionId ?: cartItems.first().sessionId

        // Fetch session from event module
        val session = eventSessionRepository.findById(sessionId)
            .orElseThrow { VenuesException.ResourceNotFound("Event session not found") }

        val event = session.event

        return CartData(
            cartSeats = cartSeats,
            cartItems = cartItems,
            session = session,
            event = event
        )
    }

    /**
     * Create booking entity from cart data.
     *
     * Centralizes booking creation logic to follow DRY principle.
     *
     * @param cartData Validated cart data
     * @param userId Optional user ID
     * @param guest Optional guest
     * @param platformId Optional platform ID
     * @param reservationToken Reservation token
     * @param paymentReference Optional payment reference
     * @return Created booking entity (not saved yet)
     */
    private fun createBookingFromCartData(
        cartData: CartData,
        userId: Long?,
        guest: app.venues.booking.domain.Guest?,
        platformId: Long?,
        reservationToken: UUID,
        paymentReference: String? = null
    ): Booking {
        val session = cartData.session
        val event = cartData.event
        val venueId = event.venueId

        // Calculate total and create booking items
        var totalPrice = BigDecimal.ZERO
        val bookingItems = mutableListOf<BookingItem>()

        // Add seat items
        cartData.cartSeats.forEach { cartSeat ->
            val config = sessionSeatConfigRepository.findBySessionIdAndSeatId(session.id!!, cartSeat.seatId)
                ?: throw VenuesException.ValidationFailure("Seat config not found")

            val item = BookingItem(
                booking = Booking(
                    userId = userId,
                    guest = guest,
                    sessionId = session.id!!,
                    reservationToken = reservationToken,
                    platformId = platformId,
                    venueId = venueId,
                    totalPrice = BigDecimal.ZERO,
                    currency = event.currency,
                    paymentId = paymentReference
                ),
                seatId = cartSeat.seatId,
                sessionSeatConfigId = config.id,
                quantity = 1,
                unitPrice = cartSeat.unitPrice,
                priceTemplateName = config.priceTemplate?.templateName
            )
            bookingItems.add(item)
            totalPrice = totalPrice.add(cartSeat.unitPrice)
        }

        // Add GA items
        cartData.cartItems.forEach { cartItem ->
            val config = sessionLevelConfigRepository.findBySessionIdAndLevelId(session.id!!, cartItem.levelId)
                ?: throw VenuesException.ValidationFailure("Level config not found")

            val itemTotal = cartItem.unitPrice.multiply(BigDecimal(cartItem.quantity))
            val item = BookingItem(
                booking = Booking(
                    userId = userId,
                    guest = guest,
                    sessionId = session.id!!,
                    reservationToken = reservationToken,
                    platformId = platformId,
                    venueId = venueId,
                    totalPrice = BigDecimal.ZERO,
                    currency = event.currency,
                    paymentId = paymentReference
                ),
                levelId = cartItem.levelId,
                quantity = cartItem.quantity,
                unitPrice = cartItem.unitPrice,
                priceTemplateName = config.priceTemplate?.templateName
            )
            bookingItems.add(item)
            totalPrice = totalPrice.add(itemTotal)
        }

        // Create booking
        val booking = Booking(
            userId = userId,
            guest = guest,
            sessionId = session.id!!,
            reservationToken = reservationToken,
            platformId = platformId,
            venueId = venueId,
            totalPrice = totalPrice,
            currency = event.currency,
            paymentId = paymentReference
        )

        // Add items to booking
        bookingItems.forEach { booking.addItem(it) }

        return booking
    }

    /**
     * Delete cart items by reservation token.
     *
     * Centralizes cart cleanup logic to follow DRY principle.
     *
     * @param reservationToken Cart reservation token
     */
    private fun deleteCartItems(reservationToken: UUID) {
        cartSeatRepository.deleteByReservationToken(reservationToken)
        cartItemRepository.deleteByReservationToken(reservationToken)
    }

    // ===========================================
    // PLATFORM INTEGRATION
    // ===========================================

    /**
     * Create booking from cart for platform integration.
     * Used when external platforms complete payment and need to finalize booking.
     *
     * @param reservationToken The cart reservation token
     * @param platformId Platform ID that initiated the booking
     * @param paymentMethod Payment method used
     * @param paymentReference External payment reference
     * @param guestEmail Optional guest email
     * @param guestName Optional guest name
     * @param guestPhone Optional guest phone
     * @return Completed booking
     */
    fun createBookingFromCart(
        reservationToken: UUID,
        platformId: Long,
        paymentMethod: String,
        paymentReference: String,
        guestEmail: String? = null,
        guestName: String? = null,
        guestPhone: String? = null
    ): Booking {
        logger.debug { "Creating booking from cart for platform $platformId: token=$reservationToken" }

        // Validate cart and get session
        val cartData = validateCartAndGetSession(reservationToken)

        // Create or find guest if email provided
        val guest = if (guestEmail != null && guestName != null) {
            guestService.findOrCreateGuest(guestEmail, guestName, guestPhone ?: "")
        } else null

        // Create booking with items
        val booking = createBookingFromCartData(
            cartData = cartData,
            userId = null,
            guest = guest,
            platformId = platformId,
            reservationToken = reservationToken,
            paymentReference = paymentReference
        )

        // Confirm immediately (payment already done by platform)
        booking.confirm(paymentReference)

        // Save booking
        val savedBooking = bookingRepository.save(booking)

        // Delete cart items
        deleteCartItems(reservationToken)

        logger.info { "Booking created from platform $platformId: bookingId=${savedBooking.id}, total=${booking.totalPrice}, venueId=${cartData.event.venueId}" }

        return savedBooking
    }
}
