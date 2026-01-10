package app.venues.booking.service

import app.venues.audit.service.AuditActionRecorder
import app.venues.booking.api.dto.BookingResponse
import app.venues.booking.api.dto.DirectSaleItemRequest
import app.venues.booking.api.dto.DirectSaleRequest
import app.venues.booking.domain.Guest
import app.venues.booking.domain.SalesChannel
import app.venues.booking.repository.BookingRepository
import app.venues.event.api.EventApi
import app.venues.event.api.dto.EventSessionDto
import app.venues.seating.api.SeatingApi
import app.venues.seating.api.dto.SeatInfoDto
import app.venues.venue.api.VenueApi
import app.venues.venue.api.dto.PromoCodeDto
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.math.BigDecimal
import java.time.Instant
import java.util.*

class DirectSalesServiceEasyReserveTest {

    private lateinit var bookingRepository: BookingRepository
    private lateinit var guestService: GuestService
    private lateinit var eventApi: EventApi
    private lateinit var seatingApi: SeatingApi
    private lateinit var venueApi: VenueApi
    private lateinit var bookingResponseService: BookingResponseService
    private lateinit var bookingFulfillmentService: BookingFulfillmentService
    private lateinit var eventPublisher: ApplicationEventPublisher
    private lateinit var auditActionRecorder: AuditActionRecorder

    private lateinit var service: DirectSalesService

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        bookingRepository = mockk(relaxed = true)
        guestService = mockk(relaxed = true)
        eventApi = mockk(relaxed = true)
        seatingApi = mockk(relaxed = true)
        venueApi = mockk(relaxed = true)
        bookingResponseService = mockk(relaxed = true)
        bookingFulfillmentService = mockk(relaxed = true)
        eventPublisher = mockk(relaxed = true)
        auditActionRecorder = mockk(relaxed = true)

        service = DirectSalesService(
            bookingRepository,
            guestService,
            eventApi,
            seatingApi,
            venueApi,
            bookingResponseService,
            bookingFulfillmentService,
            eventPublisher,
            auditActionRecorder,
            BigDecimal.ZERO
        )
    }

    @Test
    fun `does not redeem or finalize when confirmBooking is false (easy reserve)`() {
        val sessionId = UUID.randomUUID()
        val seatingChartId = UUID.randomUUID()
        val venueId = UUID.randomUUID()
        val platformId = UUID.randomUUID()

        val seatInfo = SeatInfoDto(
            id = 1L,
            code = "S1",
            seatNumber = "1",
            rowLabel = "A",
            zoneId = 10L,
            zoneName = "Zone A",
            categoryKey = "CAT"
        )
        val guest = Guest(
            email = "customer@example.com",
            name = "Jane Customer",
            phone = "123456789"
        )

        every { eventApi.getEventSessionInfo(sessionId) } returns EventSessionDto(
            sessionId = sessionId,
            eventId = UUID.randomUUID(),
            venueId = venueId,
            seatingChartId = seatingChartId,
            eventTitle = "Show",
            eventDescription = null,
            currency = "USD",
            startTime = Instant.now(),
            endTime = Instant.now().plusSeconds(3600)
        )
        every { seatingApi.getSeatInfoByCode(seatingChartId, "S1") } returns seatInfo
        every { eventApi.reserveSeat(sessionId, seatInfo.id) } returns BigDecimal("50.00")
        every { eventApi.getSeatPriceTemplateNames(sessionId, listOf(seatInfo.id)) } returns mapOf(seatInfo.id to "STD")
        every { guestService.findOrCreateGuest(any(), any(), any(), any()) } returns guest
        every { bookingRepository.save(any()) } answers { firstArg() }
        every { bookingResponseService.prepareBookingResponse(any()) } returns mockk<BookingResponse>(relaxed = true)
        every { venueApi.validatePromoCode(any(), any()) } returns mockk<PromoCodeDto>(relaxed = true)

        val request = DirectSaleRequest(
            sessionId = sessionId,
            customerEmail = guest.email,
            customerName = guest.name,
            customerPhone = guest.phone,
            items = listOf(DirectSaleItemRequest(seatCode = "S1")),
            paymentReference = null,
            promoCode = null
        )

        service.createDirectSale(
            request = request,
            venueId = venueId,
            staffId = null,
            platformId = platformId,
            salesChannel = SalesChannel.PLATFORM,
            confirmBooking = false
        )

        verify(exactly = 0) { bookingFulfillmentService.redeemPromoIfNeeded(any()) }
        verify(exactly = 0) { bookingFulfillmentService.finalizeBookingInventory(any()) }
        verify(exactly = 0) { bookingFulfillmentService.generateTickets(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
        verify(exactly = 1) { bookingResponseService.prepareBookingResponse(any()) }
    }

    @Test
    fun `Direct sale reserves items and confirms booking atomically`() {
        val sessionId = UUID.randomUUID()
        val seatingChartId = UUID.randomUUID()
        val venueId = UUID.randomUUID()
        val seatInfo = SeatInfoDto(
            id = 100L,
            code = "S1",
            seatNumber = "1",
            rowLabel = "A",
            zoneId = 10L,
            zoneName = "Zone A",
            categoryKey = "CAT"
        )
        val guest = Guest(
            email = "customer@example.com",
            name = "Jane Customer",
            phone = "123456789"
        )

        every { eventApi.getEventSessionInfo(sessionId) } returns EventSessionDto(
            sessionId = sessionId,
            eventId = UUID.randomUUID(),
            venueId = venueId,
            seatingChartId = seatingChartId,
            eventTitle = "Show",
            eventDescription = null,
            currency = "USD",
            startTime = Instant.now(),
            endTime = Instant.now().plusSeconds(3600)
        )
        every { seatingApi.getSeatInfoByCode(seatingChartId, "S1") } returns seatInfo
        every { eventApi.reserveSeat(sessionId, seatInfo.id) } returns BigDecimal("50.00")
        every { eventApi.getSeatPriceTemplateNames(sessionId, listOf(seatInfo.id)) } returns mapOf(seatInfo.id to "STD")
        every { guestService.findOrCreateGuest(any(), any(), any(), any()) } returns guest
        every { bookingRepository.save(any()) } answers { firstArg() }
        every { bookingResponseService.prepareBookingResponse(any()) } returns mockk<BookingResponse>(relaxed = true)
        every { venueApi.validatePromoCode(any(), any()) } returns mockk<PromoCodeDto>(relaxed = true)

        val request = DirectSaleRequest(
            sessionId = sessionId,
            customerEmail = guest.email,
            customerName = guest.name,
            customerPhone = guest.phone,
            items = listOf(DirectSaleItemRequest(seatCode = "S1")),
            paymentReference = null,
            promoCode = null
        )

        service.createDirectSale(
            request = request,
            venueId = venueId,
            staffId = null,
            platformId = UUID.randomUUID(),
            salesChannel = SalesChannel.PLATFORM,
            confirmBooking = true
        )

        verify(exactly = 1) { bookingFulfillmentService.redeemPromoIfNeeded(any()) }
        verify(exactly = 1) { bookingFulfillmentService.finalizeBookingInventory(any()) }
        verify(exactly = 1) { bookingFulfillmentService.generateTickets(any()) }
        verify(exactly = 1) { bookingResponseService.prepareBookingResponse(any()) }
    }
}
