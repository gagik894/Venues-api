package app.venues.booking.service

import app.venues.booking.api.CartQueryApi
import app.venues.booking.api.dto.AddSeatToCartRequest
import app.venues.booking.api.dto.CartSummaryResponse
import app.venues.booking.api.dto.PlatformHoldBatchRequest
import app.venues.booking.domain.Cart
import app.venues.booking.manager.CartSessionManager
import app.venues.booking.persistence.CartItemPersistence
import app.venues.booking.persistence.CartTablePersistence
import app.venues.booking.persistence.InventoryReservationHandler
import app.venues.booking.repository.CartRepository
import app.venues.booking.validation.CartLimitValidator
import app.venues.common.exception.VenuesException
import app.venues.event.api.EventApi
import app.venues.seating.api.SeatingApi
import io.mockk.every
import io.mockk.mockk
import jakarta.validation.Validator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

class CartServicePlatformHoldTest {

    private lateinit var cartRepository: CartRepository
    private lateinit var cartSessionManager: CartSessionManager
    private lateinit var cartLimitValidator: CartLimitValidator
    private lateinit var inventoryReservation: InventoryReservationHandler
    private lateinit var cartItemPersistence: CartItemPersistence
    private lateinit var cartTablePersistence: CartTablePersistence
    private lateinit var tableReservationService: TableReservationService
    private lateinit var eventApi: EventApi
    private lateinit var seatingApi: SeatingApi
    private lateinit var venueApi: app.venues.venue.api.VenueApi
    private lateinit var validator: Validator
    private lateinit var cartQueryApi: CartQueryApi
    private lateinit var service: CartService

    @BeforeEach
    fun setup() {
        cartRepository = mockk(relaxed = true)
        cartSessionManager = CartSessionManager(cartRepository)
        cartLimitValidator = mockk(relaxed = true)
        inventoryReservation = mockk(relaxed = true)
        cartItemPersistence = mockk(relaxed = true)
        cartTablePersistence = mockk(relaxed = true)
        tableReservationService = mockk(relaxed = true)
        eventApi = mockk()
        seatingApi = mockk()
        venueApi = mockk()
        validator = mockk(relaxed = true)
        cartQueryApi = mockk(relaxed = true)

        service = CartService(
            cartRepository,
            cartSessionManager,
            cartLimitValidator,
            inventoryReservation,
            cartItemPersistence,
            cartTablePersistence,
            tableReservationService,
            eventApi,
            seatingApi,
            venueApi,
            validator,
            cartQueryApi
        )
    }

    @Test
    fun `holdBatch requires at least one item`() {
        val request = PlatformHoldBatchRequest(
            sessionId = UUID.randomUUID(),
            platformId = UUID.randomUUID(),
            seatIdentifiers = emptyList(),
            gaReservations = emptyList(),
            tableIdentifiers = emptyList()
        )
        every { eventApi.getEventSessionInfo(any()) } returns mockk {
            every { currency } returns "USD"
            every { eventTitle } returns "event"
            every { venueId } returns UUID.randomUUID()
        }
        val ex = assertThrows(VenuesException.ValidationFailure::class.java) {
            service.holdBatch(request)
        }
        assertTrue(ex.message?.contains("At least one seat/GA/table") == true)
    }

    @Test
    fun `holdBatch enforces platform binding and returns summary`() {
        val sessionId = UUID.randomUUID()
        val seatingChartId = UUID.randomUUID()
        val platformId = UUID.randomUUID()
        val cart = Cart(sessionId = sessionId, expiresAt = java.time.Instant.now().plusSeconds(300))
        cart.platformId = platformId

        every { validator.validate(any<AddSeatToCartRequest>()) } returns emptySet()
        every { eventApi.getEventSessionInfo(sessionId) } returns mockk {
            every { currency } returns "USD"
            every { eventTitle } returns "event"
            every { venueId } returns UUID.randomUUID()
            every { this@mockk.seatingChartId } returns seatingChartId
        }
        every { seatingApi.getSeatInfoByCode(seatingChartId, "A1") } returns mockk {
            every { id } returns 1L
            every { code } returns "A1"
        }
        every {
            inventoryReservation.reserveSeat(
                sessionId,
                "A1"
            )
        } returns InventoryReservationHandler.SeatReservationResult(1L, java.math.BigDecimal.ONE)
        every { cartItemPersistence.saveSeatToCart(any(), any(), any(), any(), any()) } returns mockk(relaxed = true)
        every { cartRepository.findByToken(any()) } returns null
        every { cartRepository.save(any()) } answers { firstArg<Cart>() }

        every { cartQueryApi.getCartSummary(any()) } returns CartSummaryResponse(
            token = UUID.randomUUID(),
            seats = emptyList(),
            gaItems = emptyList(),
            tables = emptyList(),
            totalPrice = app.venues.shared.money.MoneyAmount.zero("USD"),
            currency = "USD",
            expiresAt = "2025-01-01T00:00:00Z",
            sessionId = sessionId,
            eventTitle = "event"
        )

        val summary = service.holdBatch(
            PlatformHoldBatchRequest(
                sessionId = sessionId,
                platformId = platformId,
                seatIdentifiers = listOf("A1"),
                gaReservations = null,
                tableIdentifiers = null
            )
        )

        assertEquals("USD", summary.currency)
        assertEquals("event", summary.eventTitle)
    }
}

