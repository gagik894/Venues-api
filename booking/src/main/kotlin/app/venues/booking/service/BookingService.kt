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
 * Uses API interfaces (UserApi, SeatingApi) for cross-module communication,
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
    // API interfaces for cross-module communication (Hexagonal Architecture)
    private val userApi: UserApi,
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

        // Get cart items
        val cartSeats = cartSeatRepository.findByReservationToken(request.token)
        val cartItems = cartItemRepository.findByReservationToken(request.token)

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

        // Get event from session for currency
        val event = session.event

        // Determine user or guest
        val guest = if (userId == null) {
            guestService.findOrCreateGuest(request.email, request.name, request.phone)
        } else null

        // Calculate total and create booking items
        var totalPrice = BigDecimal.ZERO
        val bookingItems = mutableListOf<BookingItem>()

        // Add seat items
        cartSeats.forEach { cartSeat ->
            val config = sessionSeatConfigRepository.findBySessionIdAndSeatId(sessionId, cartSeat.seatId)
                ?: throw VenuesException.ValidationFailure("Seat config not found")

            val item = BookingItem(
                booking = Booking(
                    userId = userId,
                    guest = guest,
                    sessionId = sessionId,
                    reservationToken = request.token,
                    totalPrice = BigDecimal.ZERO, // Will update later
                    currency = event.currency
                ),
                seatId = cartSeat.seatId,
                sessionSeatConfigId = config.id,
                quantity = 1,
                unitPrice = config.price,
                priceTemplateName = config.priceTemplate?.templateName
            )
            bookingItems.add(item)
            totalPrice = totalPrice.add(config.price)
        }

        // Add GA items
        cartItems.forEach { cartItem ->
            val config = sessionLevelConfigRepository.findBySessionIdAndLevelId(sessionId, cartItem.levelId)
                ?: throw VenuesException.ValidationFailure("Level config not found")

            val itemTotal = config.price.multiply(BigDecimal(cartItem.quantity))
            val item = BookingItem(
                booking = Booking(
                    userId = userId,
                    guest = guest,
                    sessionId = sessionId,
                    reservationToken = request.token,
                    totalPrice = BigDecimal.ZERO,
                    currency = event.currency
                ),
                levelId = cartItem.levelId,
                quantity = cartItem.quantity,
                unitPrice = config.price,
                priceTemplateName = config.priceTemplate?.templateName
            )
            bookingItems.add(item)
            totalPrice = totalPrice.add(itemTotal)
        }

        // Create booking
        val booking = Booking(
            userId = userId,
            guest = guest,
            sessionId = sessionId,
            reservationToken = request.token,
            totalPrice = totalPrice,
            currency = event.currency
        )

        // Add items to booking
        bookingItems.forEach { booking.addItem(it) }

        // Save booking
        val savedBooking = bookingRepository.save(booking)

        // Delete cart items
        cartSeatRepository.deleteByReservationToken(request.token)
        cartItemRepository.deleteByReservationToken(request.token)

        logger.info { "Checkout completed: bookingId=${savedBooking.id}, total=$totalPrice" }

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

    /**
     * Prepare booking response by fetching all cross-module data using API interfaces.
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

        return bookingMapper.toResponse(
            booking = booking,
            eventTitle = event.title,
            eventDescription = event.description,
            venueName = "Unknown", // TODO: Fetch from VenueApi
            sessionStartTime = session.startTime.toString(),
            sessionEndTime = session.endTime.toString(),
            customerEmail = customerEmail,
            customerName = customerName,
            itemsData = itemsData
        )
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
        val venueId = event.venueId

        // Create or find guest if email provided
        val guest = if (guestEmail != null && guestName != null) {
            guestService.findOrCreateGuest(guestEmail, guestName, guestPhone ?: "")
        } else null

        // Calculate total and create booking items
        var totalPrice = BigDecimal.ZERO
        val bookingItems = mutableListOf<BookingItem>()

        // Add seat items
        cartSeats.forEach { cartSeat ->
            val config = sessionSeatConfigRepository.findBySessionIdAndSeatId(sessionId, cartSeat.seatId)
                ?: throw VenuesException.ValidationFailure("Seat config not found")

            val item = BookingItem(
                booking = Booking(
                    guest = guest,
                    sessionId = sessionId,
                    reservationToken = reservationToken,
                    platformId = platformId,
                    venueId = venueId,
                    totalPrice = BigDecimal.ZERO,
                    currency = event.currency
                ),
                seatId = cartSeat.seatId,
                sessionSeatConfigId = config.id,
                quantity = 1,
                unitPrice = config.price,
                priceTemplateName = config.priceTemplate?.templateName
            )
            bookingItems.add(item)
            totalPrice = totalPrice.add(config.price)
        }

        // Add GA items
        cartItems.forEach { cartItem ->
            val config = sessionLevelConfigRepository.findBySessionIdAndLevelId(sessionId, cartItem.levelId)
                ?: throw VenuesException.ValidationFailure("Level config not found")

            val itemTotal = config.price.multiply(BigDecimal(cartItem.quantity))
            val item = BookingItem(
                booking = Booking(
                    guest = guest,
                    sessionId = sessionId,
                    reservationToken = reservationToken,
                    platformId = platformId,
                    venueId = venueId,
                    totalPrice = BigDecimal.ZERO,
                    currency = event.currency
                ),
                levelId = cartItem.levelId,
                quantity = cartItem.quantity,
                unitPrice = config.price,
                priceTemplateName = config.priceTemplate?.templateName
            )
            bookingItems.add(item)
            totalPrice = totalPrice.add(itemTotal)
        }

        // Create booking
        val booking = Booking(
            guest = guest,
            sessionId = sessionId,
            reservationToken = reservationToken,
            platformId = platformId,
            venueId = venueId,
            totalPrice = totalPrice,
            currency = event.currency,
            paymentId = paymentReference
        )

        // Add items to booking
        bookingItems.forEach { booking.addItem(it) }

        // Confirm immediately (payment already done by platform)
        booking.confirm(paymentReference)

        // Save booking
        val savedBooking = bookingRepository.save(booking)

        // Delete cart items
        cartSeatRepository.deleteByReservationToken(reservationToken)
        cartItemRepository.deleteByReservationToken(reservationToken)

        logger.info { "Booking created from platform $platformId: bookingId=${savedBooking.id}, total=$totalPrice, venueId=$venueId" }

        return savedBooking
    }
}


