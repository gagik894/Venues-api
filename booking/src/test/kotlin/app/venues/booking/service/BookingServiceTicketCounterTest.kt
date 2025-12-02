package app.venues.booking.service

import app.venues.booking.api.dto.BookingResponse
import app.venues.booking.api.dto.CancelBookingRequest
import app.venues.booking.api.dto.ConfirmBookingRequest
import app.venues.booking.api.mapper.BookingMapper
import app.venues.booking.domain.Booking
import app.venues.booking.domain.BookingItem
import app.venues.booking.domain.SalesChannel
import app.venues.booking.repository.BookingRepository
import app.venues.booking.repository.CartRepository
import app.venues.event.api.EventApi
import app.venues.event.api.dto.EventSessionDto
import app.venues.seating.api.SeatingApi
import app.venues.seating.api.dto.GaInfoDto
import app.venues.seating.api.dto.SeatInfoDto
import app.venues.ticket.api.TicketApi
import app.venues.user.api.UserApi
import app.venues.venue.api.VenueApi
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.math.BigDecimal
import java.time.Instant
import java.util.*

class BookingServiceTicketCounterTest {

    private lateinit var bookingRepository: BookingRepository
    private lateinit var cartRepository: CartRepository
    private lateinit var guestService: GuestService
    private lateinit var creationService: BookingCreationService
    private lateinit var bookingMapper: BookingMapper
    private lateinit var userApi: UserApi
    private lateinit var seatingApi: SeatingApi
    private lateinit var eventApi: EventApi
    private lateinit var venueApi: VenueApi
    private lateinit var ticketApi: TicketApi
    private lateinit var eventPublisher: ApplicationEventPublisher
    private lateinit var service: BookingService

    @BeforeEach
    fun setup() {
        bookingRepository = mockk()
        cartRepository = mockk(relaxed = true)
        guestService = mockk(relaxed = true)
        creationService = mockk(relaxed = true)
        bookingMapper = mockk()
        userApi = mockk(relaxed = true)
        seatingApi = mockk()
        eventApi = mockk()
        venueApi = mockk(relaxed = true)
        ticketApi = mockk(relaxed = true)
        eventPublisher = mockk(relaxed = true)

        val bookingFulfillmentService = BookingFulfillmentService(
            eventApi,
            seatingApi,
            venueApi,
            ticketApi
        )

        service = BookingService(
            bookingRepository,
            cartRepository,
            guestService,
            creationService,
            bookingMapper,
            userApi,
            seatingApi,
            eventApi,
            venueApi,
            ticketApi,
            bookingFulfillmentService,
            eventPublisher
        )

        every {
            bookingMapper.toResponse(any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            val args = invocation.args
            val bookingArg = args[0] as Booking
            BookingResponse(
                id = bookingArg.id.toString(),
                sessionId = bookingArg.sessionId,
                eventTitle = args[1] as String,
                eventDescription = args[2] as String?,
                sessionStartTime = args[3] as String,
                sessionEndTime = args[4] as String,
                customerEmail = args[5] as String,
                customerName = args[6] as String,
                items = emptyList(),
                totalPrice = bookingArg.totalPrice.toString(),
                serviceFeeAmount = bookingArg.serviceFeeAmount.toString(),
                discountAmount = bookingArg.discountAmount.toString(),
                promoCode = bookingArg.promoCode,
                currency = bookingArg.currency,
                status = bookingArg.status,
                confirmedAt = bookingArg.confirmedAt?.toString(),
                cancelledAt = bookingArg.cancelledAt?.toString(),
                cancellationReason = bookingArg.cancellationReason,
                paymentId = bookingArg.paymentId?.toString(),
                createdAt = bookingArg.createdAt.toString()
            )
        }
    }

    @Test
    fun `confirmBooking increments tickets sold for seats GA and tables`() {
        val sessionId = UUID.randomUUID()
        val venueId = UUID.randomUUID()
        val booking = Booking(
            userId = UUID.randomUUID(),
            guest = null,
            sessionId = sessionId,
            totalPrice = BigDecimal("120.00"),
            currency = "AMD",
            platformId = null,
            venueId = venueId,
            externalOrderNumber = null,
            serviceFeeAmount = BigDecimal.ZERO,
            discountAmount = BigDecimal.ZERO,
            promoCode = null,
            salesChannel = SalesChannel.WEBSITE,
            staffId = null
        )
        val seatItem = BookingItem(booking, quantity = 1, unitPrice = BigDecimal("40.00"), seatId = 10L)
        val gaItem = BookingItem(booking, quantity = 3, unitPrice = BigDecimal("20.00"), gaAreaId = 20L)
        val tableItem = BookingItem(booking, quantity = 1, unitPrice = BigDecimal("40.00"), tableId = 30L)
        booking.addItem(seatItem)
        booking.addItem(gaItem)
        booking.addItem(tableItem)
        seatItem.id = 1L
        gaItem.id = 2L
        tableItem.id = 3L

        val bookingId = booking.id
        val request = ConfirmBookingRequest(paymentId = UUID.randomUUID())
        val sessionDto = EventSessionDto(
            sessionId = sessionId,
            eventId = UUID.randomUUID(),
            venueId = venueId,
            eventTitle = "Concert",
            eventDescription = "Test",
            currency = "AMD",
            startTime = Instant.now(),
            endTime = Instant.now().plusSeconds(3600)
        )

        every { bookingRepository.findById(bookingId) } returns Optional.of(booking)
        every { bookingRepository.save(any()) } answers { invocation.args[0] as Booking }
        every { eventApi.sellSeatsBatch(sessionId, listOf(10L)) } just Runs
        every { eventApi.sellGaBatch(sessionId, mapOf(20L to 3)) } just Runs
        every { eventApi.sellTablesBatch(sessionId, listOf(30L)) } just Runs
        every { eventApi.incrementTicketsSold(sessionId, 8) } returns true
        every { eventApi.getEventSessionInfo(sessionId) } returns sessionDto
        every { userApi.getUserEmail(any()) } returns "user@example.com"
        every { userApi.getUserFullName(any()) } returns "Alice"
        every { seatingApi.getSeatInfo(10L) } returns SeatInfoDto(10L, "A1", "1", "A", 1L, "Zone", "VIP")
        every { seatingApi.getGaInfo(20L) } returns GaInfoDto(
            20L, "GA", "GA", 100, 1L,
            categoryKey = "AAA"
        )
        every { seatingApi.getSeatsForTable(30L) } returns listOf(
            SeatInfoDto(101L, "T1", "1", "T", 1L, "Zone", "VIP"),
            SeatInfoDto(102L, "T2", "2", "T", 1L, "Zone", "VIP"),
            SeatInfoDto(103L, "T3", "3", "T", 1L, "Zone", "VIP"),
            SeatInfoDto(104L, "T4", "4", "T", 1L, "Zone", "VIP")
        )

        service.confirmBooking(bookingId, request, booking.userId)

        verify { eventApi.incrementTicketsSold(sessionId, 8) }
        verify {
            ticketApi.generateTicketsForBookingItem(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `cancelBooking decrements tickets sold when booking was confirmed`() {
        val sessionId = UUID.randomUUID()
        val booking = Booking(
            userId = UUID.randomUUID(),
            guest = null,
            sessionId = sessionId,
            totalPrice = BigDecimal("60.00"),
            currency = "AMD",
            platformId = null,
            venueId = null,
            externalOrderNumber = null,
            serviceFeeAmount = BigDecimal.ZERO,
            discountAmount = BigDecimal.ZERO,
            promoCode = null,
            salesChannel = SalesChannel.WEBSITE,
            staffId = null
        )
        val seatItem = BookingItem(booking, quantity = 1, unitPrice = BigDecimal("30.00"), seatId = 11L)
        val gaItem = BookingItem(booking, quantity = 2, unitPrice = BigDecimal("15.00"), gaAreaId = 21L)
        booking.addItem(seatItem)
        booking.addItem(gaItem)
        seatItem.id = 11L
        gaItem.id = 12L
        booking.confirm(UUID.randomUUID())

        val bookingId = booking.id
        val sessionDto = EventSessionDto(
            sessionId = sessionId,
            eventId = UUID.randomUUID(),
            venueId = UUID.randomUUID(),
            eventTitle = "Concert",
            eventDescription = "Test",
            currency = "AMD",
            startTime = Instant.now(),
            endTime = Instant.now().plusSeconds(3600)
        )

        every { bookingRepository.findById(bookingId) } returns Optional.of(booking)
        every { bookingRepository.save(any()) } answers { invocation.args[0] as Booking }
        every { eventApi.releaseSeatsBatch(sessionId, listOf(11L)) } just Runs
        every { eventApi.releaseGaBatch(sessionId, mapOf(21L to 2)) } just Runs
        every { eventApi.releaseTablesBatch(sessionId, emptyList()) } just Runs
        every { eventApi.decrementTicketsSold(sessionId, 3) } returns true
        every { eventApi.getEventSessionInfo(sessionId) } returns sessionDto
        every { userApi.getUserEmail(any()) } returns "user@example.com"
        every { userApi.getUserFullName(any()) } returns "Bob"
        every { seatingApi.getSeatInfo(11L) } returns SeatInfoDto(11L, "B1", "1", "B", 1L, "Zone", "VIP")
        every { seatingApi.getGaInfo(21L) } returns GaInfoDto(21L, "GA", "GA", 100, 1L, categoryKey = "AAA")

        service.cancelBooking(bookingId, CancelBookingRequest("Customer request"), booking.userId)

        verify { eventApi.decrementTicketsSold(sessionId, 3) }
        verify { ticketApi.invalidateTicketsForBooking(bookingId, any(), any()) }
    }
}
