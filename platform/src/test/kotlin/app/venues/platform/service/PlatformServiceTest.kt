package app.venues.platform.service

import app.venues.booking.api.BookingApi
import app.venues.booking.api.CartApi
import app.venues.booking.api.CartQueryApi
import app.venues.booking.api.dto.*
import app.venues.platform.api.dto.PlatformGAReservation
import app.venues.platform.api.dto.PlatformReservationRequest
import app.venues.platform.api.dto.PlatformReservationResponse
import app.venues.platform.domain.Platform
import app.venues.platform.repository.PlatformRepository
import app.venues.platform.repository.WebhookEventRepository
import app.venues.seating.api.SeatingApi
import app.venues.shared.money.MoneyAmount
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.util.*

@ExtendWith(io.mockk.junit5.MockKExtension::class)
class PlatformServiceTest {

    @MockK
    private lateinit var platformRepository: PlatformRepository

    @MockK
    private lateinit var webhookEventRepository: WebhookEventRepository

    @MockK
    private lateinit var cartApi: CartApi

    @MockK
    private lateinit var cartQueryApi: CartQueryApi

    @MockK
    private lateinit var bookingApi: BookingApi

    @MockK
    private lateinit var seatingApi: SeatingApi

    @MockK
    private lateinit var rateLimitService: PlatformRateLimitService

    @MockK
    private lateinit var idempotencyService: PlatformIdempotencyService

    private lateinit var service: PlatformService

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        clearMocks(platformRepository, webhookEventRepository, cartApi, cartQueryApi, bookingApi, seatingApi)
        service = PlatformService(
            platformRepository,
            webhookEventRepository,
            cartApi,
            cartQueryApi,
            bookingApi,
            seatingApi,
            rateLimitService,
            idempotencyService
        )
    }

    @Test
    fun `reserveSeats reuses existing token and adjusts GA while skipping duplicates`() {
        val platformId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val existingToken = UUID.randomUUID()
        val platform = Platform(
            name = "TestPlatform",
            apiUrl = "https://api.test",
            sharedSecret = "secret"
        )

        val existingSummary = CartSummaryResponse(
            token = existingToken,
            seats = listOf(
                CartSeatResponse(
                    code = "S1",
                    number = "1",
                    rowLabel = "A",
                    levelName = "VIP",
                    price = MoneyAmount(BigDecimal("50.00"), "USD"),
                    priceTemplateName = "VIP"
                )
            ),
            gaItems = listOf(
                CartGAItemResponse(
                    code = "GA1",
                    name = "Floor",
                    quantity = 1,
                    unitPrice = MoneyAmount(BigDecimal("20.00"), "USD"),
                    totalPrice = MoneyAmount(BigDecimal("20.00"), "USD"),
                    priceTemplateName = "GA"
                )
            ),
            tables = listOf(
                CartTableResponse(
                    code = "T1",
                    number = "Table 1",
                    price = MoneyAmount(BigDecimal("200.00"), "USD")
                )
            ),
            totalPrice = MoneyAmount(BigDecimal("270.00"), "USD"),
            currency = "USD",
            expiresAt = "2025-01-01T00:00:00Z",
            sessionId = sessionId,
            eventTitle = "Event"
        )

        val finalSummary = existingSummary.copy(
            gaItems = listOf(
                CartGAItemResponse(
                    code = "GA1",
                    name = "Floor",
                    quantity = 2,
                    unitPrice = MoneyAmount(BigDecimal("20.00"), "USD"),
                    totalPrice = MoneyAmount(BigDecimal("40.00"), "USD"),
                    priceTemplateName = "GA"
                )
            ),
            totalPrice = MoneyAmount(BigDecimal("290.00"), "USD"),
            expiresAt = "2025-01-01T00:05:00Z"
        )

        every { platformRepository.findById(platformId) } returns Optional.of(platform)
        every { cartQueryApi.getCartSummary(existingToken) } returnsMany listOf(existingSummary, finalSummary)
        every {
            cartApi.updateGAQuantity(
                token = existingToken,
                levelIdentifier = "GA1",
                request = UpdateGAQuantityRequest(quantity = 2)
            )
        } returns finalSummary
        every { cartApi.addSeatToCart(any(), any(), any()) } throws IllegalStateException("should not be called")
        every { cartApi.addGAToCart(any(), any(), any()) } throws IllegalStateException("should not be called")
        every { cartApi.addTableToCart(any(), any(), any()) } throws IllegalStateException("should not be called")
        every { rateLimitService.enforce(platformId, any()) } returns Unit
        every {
            idempotencyService.withIdempotency(
                idempotencyKey = any(),
                platformId = platformId,
                endpoint = any(),
                responseType = PlatformReservationResponse::class.java,
                supplier = any()
            )
        } answers {
            val supplier = arg<() -> PlatformReservationResponse>(4)
            supplier.invoke()
        }

        val result = service.reserveSeats(
            platformId = platformId,
            request = PlatformReservationRequest(
                sessionId = sessionId,
                reservationToken = existingToken,
                seatIdentifiers = listOf("S1"),
                gaReservations = listOf(PlatformGAReservation(levelIdentifier = "GA1", quantity = 2)),
                tableIdentifiers = listOf("T1"),
                guestEmail = null,
                guestName = null,
                externalReference = null
            ),
            idempotencyKey = "key-1"
        )

        assertEquals(existingToken, result.reservationToken)
        assertEquals(2, result.gaTickets?.single()?.quantity)
        assertEquals("S1", result.seats?.single()?.seatIdentifier)
        assertEquals("T1", result.tables?.single()?.tableIdentifier)

        verify(exactly = 1) { cartApi.updateGAQuantity(existingToken, "GA1", UpdateGAQuantityRequest(quantity = 2)) }
    }

    @Test
    fun `reserveSeats creates new reservation token and adds all item types`() {
        val platformId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val platform = Platform(
            name = "TestPlatform",
            apiUrl = "https://api.test",
            sharedSecret = "secret"
        )

        val tokenSlot: CapturingSlot<UUID> = slot()

        every { platformRepository.findById(platformId) } returns Optional.of(platform)
        every { rateLimitService.enforce(platformId, any()) } returns Unit
        every {
            idempotencyService.withIdempotency(
                idempotencyKey = null,
                platformId = platformId,
                endpoint = any(),
                responseType = PlatformReservationResponse::class.java,
                supplier = any()
            )
        } answers {
            val supplier = arg<() -> PlatformReservationResponse>(4)
            supplier.invoke()
        }

        every { cartApi.addSeatToCart(any(), capture(tokenSlot), any()) } answers {
            val token = secondArg<UUID>()
            CartSummaryResponse(
                token = token,
                seats = emptyList(),
                gaItems = emptyList(),
                tables = emptyList(),
                totalPrice = MoneyAmount(BigDecimal.ZERO, "USD"),
                currency = "USD",
                expiresAt = "2025-01-01T00:00:00Z",
                sessionId = sessionId,
                eventTitle = "Event"
            )
        }

        every { cartApi.addGAToCart(any(), any(), any()) } answers {
            val token = secondArg<UUID>()
            CartSummaryResponse(
                token = token,
                seats = emptyList(),
                gaItems = emptyList(),
                tables = emptyList(),
                totalPrice = MoneyAmount(BigDecimal.ZERO, "USD"),
                currency = "USD",
                expiresAt = "2025-01-01T00:00:10Z",
                sessionId = sessionId,
                eventTitle = "Event"
            )
        }

        every { cartApi.addTableToCart(any(), any(), any()) } answers {
            val token = secondArg<UUID>()
            CartSummaryResponse(
                token = token,
                seats = emptyList(),
                gaItems = emptyList(),
                tables = emptyList(),
                totalPrice = MoneyAmount(BigDecimal.ZERO, "USD"),
                currency = "USD",
                expiresAt = "2025-01-01T00:00:20Z",
                sessionId = sessionId,
                eventTitle = "Event"
            )
        }

        every { cartQueryApi.getCartSummary(any()) } answers {
            val token = firstArg<UUID>()
            CartSummaryResponse(
                token = token,
                seats = listOf(
                    CartSeatResponse(
                        code = "S2",
                        number = "2",
                        rowLabel = "A",
                        levelName = "VIP",
                        price = MoneyAmount(BigDecimal("30.00"), "USD"),
                        priceTemplateName = "VIP"
                    )
                ),
                gaItems = listOf(
                    CartGAItemResponse(
                        code = "GA2",
                        name = "Floor",
                        quantity = 3,
                        unitPrice = MoneyAmount(BigDecimal("15.00"), "USD"),
                        totalPrice = MoneyAmount(BigDecimal("45.00"), "USD"),
                        priceTemplateName = "GA"
                    )
                ),
                tables = listOf(
                    CartTableResponse(
                        code = "T2",
                        number = "Table 2",
                        price = MoneyAmount(BigDecimal("150.00"), "USD")
                    )
                ),
                totalPrice = MoneyAmount(BigDecimal("225.00"), "USD"),
                currency = "USD",
                expiresAt = "2025-01-01T00:01:00Z",
                sessionId = sessionId,
                eventTitle = "Event"
            )
        }

        val result = service.reserveSeats(
            platformId = platformId,
            request = PlatformReservationRequest(
                sessionId = sessionId,
                seatIdentifiers = listOf("S2"),
                gaReservations = listOf(PlatformGAReservation(levelIdentifier = "GA2", quantity = 3)),
                tableIdentifiers = listOf("T2"),
                guestEmail = null,
                guestName = null,
                externalReference = null
            ),
            idempotencyKey = null
        )

        val generatedToken = result.reservationToken
        assertTrue(tokenSlot.isCaptured)
        assertEquals(tokenSlot.captured, generatedToken)
        assertEquals("S2", result.seats?.single()?.seatIdentifier)
        assertEquals(3, result.gaTickets?.single()?.quantity)
        assertEquals("T2", result.tables?.single()?.tableIdentifier)

        verify(exactly = 1) { cartApi.addSeatToCart(any(), generatedToken, false) }
        verify(exactly = 1) { cartApi.addGAToCart(any(), generatedToken, false) }
        verify(exactly = 1) { cartApi.addTableToCart(any(), generatedToken, false) }
    }
}
