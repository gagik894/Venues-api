package app.venues.booking.service

import app.venues.booking.domain.Cart
import app.venues.booking.domain.CartItem
import app.venues.booking.domain.CartSeat
import app.venues.booking.domain.CartTable
import app.venues.booking.service.model.BookingCreationContext
import app.venues.booking.service.model.CartSnapshot
import app.venues.common.exception.VenuesException
import app.venues.event.api.EventApi
import app.venues.event.api.dto.EventSessionDto
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.*

class BookingCreationServiceTest {

    private lateinit var eventApi: EventApi
    private lateinit var service: BookingCreationService

    @BeforeEach
    fun setup() {
        eventApi = mockk()
        service = BookingCreationService(eventApi, BigDecimal("5"))
    }

    @Test
    fun `assembleBooking builds booking aggregate with correct totals`() {
        val sessionId = UUID.randomUUID()
        val venueId = UUID.randomUUID()
        val cart = Cart(
            sessionId = sessionId,
            expiresAt = Instant.now().plusSeconds(600),
            token = UUID.randomUUID(),
            promoCode = "FALL10",
            discountAmount = BigDecimal("10.00")
        )
        val seat = CartSeat(cart, sessionId, seatId = 1L, unitPrice = BigDecimal("40.00"))
        val gaItem = CartItem(cart, sessionId, gaAreaId = 2L, unitPrice = BigDecimal("20.00"), quantity = 2)
        val table = CartTable(cart, sessionId, tableId = 3L, unitPrice = BigDecimal("100.00"))
        val snapshot = CartSnapshot(
            cart = cart,
            seats = listOf(seat),
            gaItems = listOf(gaItem),
            tables = listOf(table),
            session = EventSessionDto(
                sessionId = sessionId,
                eventId = UUID.randomUUID(),
                venueId = venueId,
                eventTitle = "Sample Event",
                eventDescription = "Desc",
                currency = "USD",
                startTime = Instant.now(),
                endTime = Instant.now().plusSeconds(3600)
            )
        )

        every { eventApi.getSeatPriceTemplateNames(sessionId, listOf(1L)) } returns mapOf(1L to "VIP")
        every { eventApi.getGaPriceTemplateNames(sessionId, listOf(2L)) } returns mapOf(2L to "GA")
        every { eventApi.getTablePriceTemplateNames(sessionId, listOf(3L)) } returns mapOf(3L to "Table")

        val result = service.assembleBooking(
            snapshot = snapshot,
            context = BookingCreationContext(
                userId = UUID.randomUUID(),
                guest = null,
                platformId = null,
                paymentReference = "PAY-123"
            )
        )

        val subtotal = BigDecimal("40.00") + BigDecimal("40.00") + BigDecimal("100.00")
        assertEquals(subtotal, result.pricing.subtotal)
        assertEquals(BigDecimal("10.00"), result.pricing.discount)
        assertEquals(BigDecimal("8.50"), result.pricing.serviceFee)
        assertEquals(BigDecimal("178.50"), result.pricing.total)
        assertEquals(3, result.booking.items.size)
        assertEquals("VIP", result.booking.items.first { it.seatId != null }.priceTemplateName)
        assertEquals("GA", result.booking.items.first { it.gaAreaId != null }.priceTemplateName)
        assertEquals("Table", result.booking.items.first { it.tableId != null }.priceTemplateName)
        assertEquals("FALL10", result.booking.promoCode)
        assertEquals(BigDecimal("10.00"), result.booking.discountAmount)
    }

    @Test
    fun `assembleBooking fails when snapshot is empty`() {
        val sessionId = UUID.randomUUID()
        val cart = Cart(
            sessionId = sessionId,
            expiresAt = Instant.now().plusSeconds(600),
            token = UUID.randomUUID()
        )
        val snapshot = CartSnapshot(
            cart = cart,
            seats = emptyList(),
            gaItems = emptyList(),
            tables = emptyList(),
            session = EventSessionDto(
                sessionId = sessionId,
                eventId = UUID.randomUUID(),
                venueId = UUID.randomUUID(),
                eventTitle = "Event",
                eventDescription = null,
                currency = "USD",
                startTime = Instant.now(),
                endTime = Instant.now().plusSeconds(3600)
            )
        )

        every { eventApi.getSeatPriceTemplateNames(any(), any()) } returns emptyMap()
        every { eventApi.getGaPriceTemplateNames(any(), any()) } returns emptyMap()
        every { eventApi.getTablePriceTemplateNames(any(), any()) } returns emptyMap()

        val exception = assertThrows(VenuesException.ResourceNotFound::class.java) {
            service.assembleBooking(snapshot, BookingCreationContext(null, null, null))
        }

        assertEquals("Cart is empty", exception.message)
    }

    @Test
    fun `validateSnapshot rejects mismatched sessions`() {
        val sessionId = UUID.randomUUID()
        val otherSession = UUID.randomUUID()
        val cart = Cart(
            sessionId = sessionId,
            expiresAt = Instant.now().plusSeconds(600),
            token = UUID.randomUUID()
        )
        val seat = CartSeat(cart, otherSession, seatId = 1L, unitPrice = BigDecimal.ONE)
        val snapshot = CartSnapshot(
            cart = cart,
            seats = listOf(seat),
            gaItems = emptyList(),
            tables = emptyList(),
            session = EventSessionDto(
                sessionId = sessionId,
                eventId = UUID.randomUUID(),
                venueId = UUID.randomUUID(),
                eventTitle = "Event",
                eventDescription = null,
                currency = "USD",
                startTime = Instant.now(),
                endTime = Instant.now().plusSeconds(3600)
            )
        )

        every { eventApi.getSeatPriceTemplateNames(any(), any()) } returns emptyMap()
        every { eventApi.getGaPriceTemplateNames(any(), any()) } returns emptyMap()
        every { eventApi.getTablePriceTemplateNames(any(), any()) } returns emptyMap()

        val exception = assertThrows(VenuesException.ValidationFailure::class.java) {
            service.assembleBooking(snapshot, BookingCreationContext(null, null, null))
        }

        assertEquals("Seat 1 does not belong to session $sessionId", exception.message)
    }
}
