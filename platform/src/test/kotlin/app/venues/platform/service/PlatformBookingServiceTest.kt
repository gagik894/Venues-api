package app.venues.platform.service

import app.venues.booking.api.BookingApi
import app.venues.booking.api.CartApi
import app.venues.booking.api.CartQueryApi
import app.venues.booking.api.CartValidationApi
import app.venues.booking.api.dto.BookingResponse
import app.venues.booking.api.dto.CartGAItemResponse
import app.venues.booking.api.dto.CartSeatResponse
import app.venues.booking.api.dto.CartSummaryResponse
import app.venues.platform.api.dto.PlatformConfirmRequest
import app.venues.platform.api.dto.PlatformHoldRequest
import app.venues.platform.domain.Platform
import app.venues.platform.repository.PlatformRepository
import io.mockk.MockKAnnotations
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*

/**
 * Focused tests for the thin PlatformBookingService adapter to ensure it delegates
 * to booking/cart APIs with platform binding and respects per-platform rate limits.
 */
class PlatformBookingServiceTest {

    @MockK
    private lateinit var platformRepository: PlatformRepository

    @MockK
    private lateinit var cartApi: CartApi

    @MockK
    private lateinit var cartQueryApi: CartQueryApi

    @MockK
    private lateinit var cartValidationApi: CartValidationApi

    @MockK
    private lateinit var bookingApi: BookingApi

    @MockK
    private lateinit var rateLimitService: PlatformRateLimitService

    private lateinit var service: PlatformBookingService

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        clearMocks(
            platformRepository,
            cartApi,
            cartQueryApi,
            cartValidationApi,
            bookingApi,
            rateLimitService
        )
        service = PlatformBookingService(
            platformRepository = platformRepository,
            cartApi = cartApi,
            cartQueryApi = cartQueryApi,
            cartValidationApi = cartValidationApi,
            bookingApi = bookingApi,
            rateLimitService = rateLimitService
        )
    }

    @Test
    fun `hold delegates with platformId and uses platform rate limit`() {
        val platformId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val holdToken = UUID.randomUUID()
        val platform = Platform(
            name = "P1",
            apiUrl = "https://example.com",
            sharedSecret = "secret",
            rateLimit = 42
        )

        every { platformRepository.findById(platformId) } returns Optional.of(platform)
        every { rateLimitService.enforce(platformId, 42) } returns Unit

        // Cart API returns token/expires at; summary echoes it.
        every {
            cartApi.addSeatToCart(any(), any(), any(), any())
        } answers {
            val reqToken = secondArg<UUID?>()
            CartSummaryResponse(
                token = reqToken ?: holdToken,
                seats = emptyList(),
                gaItems = emptyList(),
                tables = emptyList(),
                totalPrice = app.venues.shared.money.MoneyAmount.zero("USD"),
                currency = "USD",
                expiresAt = "2025-01-01T00:00:00Z",
                sessionId = sessionId,
                eventTitle = "event"
            )
        }

        every {
            cartQueryApi.getCartSummary(any())
        } returns CartSummaryResponse(
            token = holdToken,
            seats = listOf(
                CartSeatResponse(
                    code = "A1",
                    number = "1",
                    rowLabel = "A",
                    levelName = "VIP",
                    price = app.venues.shared.money.MoneyAmount.zero("USD"),
                    priceTemplateName = "VIP"
                )
            ),
            gaItems = listOf(
                CartGAItemResponse(
                    code = "GA1",
                    name = "GA",
                    quantity = 1,
                    unitPrice = app.venues.shared.money.MoneyAmount.zero("USD"),
                    totalPrice = app.venues.shared.money.MoneyAmount.zero("USD"),
                    priceTemplateName = "GA"
                )
            ),
            tables = emptyList(),
            totalPrice = app.venues.shared.money.MoneyAmount.zero("USD"),
            currency = "USD",
            expiresAt = "2025-01-01T00:00:00Z",
            sessionId = sessionId,
            eventTitle = "event"
        )

        val response = service.hold(
            platformId = platformId,
            request = PlatformHoldRequest(
                sessionId = sessionId,
                seatIdentifiers = listOf("A1"),
                gaReservations = null,
                tableIdentifiers = null
            ),
        )

        assertEquals(holdToken, response.holdToken)
        assertEquals("USD", response.currency)
        verify(exactly = 1) { rateLimitService.enforce(platformId, 42) }
        verify { cartApi.addSeatToCart(any(), any(), any(), platformId) }
    }

    @Test
    fun `confirm delegates to booking with optional guest and platform binding`() {
        val platformId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val holdToken = UUID.randomUUID()

        val platform = Platform(
            name = "P1",
            apiUrl = "https://example.com",
            sharedSecret = "secret",
            rateLimit = 10
        )

        every { platformRepository.findById(platformId) } returns Optional.of(platform)
        every { rateLimitService.enforce(platformId, 10) } returns Unit

        every {
            cartValidationApi.validateCartForPlatform(
                token = holdToken,
                platformId = platformId,
                expectedSessionId = null
            )
        } returns app.venues.booking.api.CartValidationResult(
            token = holdToken,
            sessionId = sessionId,
            expiresAt = "2025-01-01T00:00:00Z"
        )

        every { cartQueryApi.getCartSummary(holdToken) } returns CartSummaryResponse(
            token = holdToken,
            seats = emptyList(),
            gaItems = emptyList(),
            tables = emptyList(),
            totalPrice = app.venues.shared.money.MoneyAmount.zero("USD"),
            currency = "USD",
            expiresAt = "2025-01-01T00:00:00Z",
            sessionId = sessionId,
            eventTitle = "event"
        )

        every {
            bookingApi.createBookingFromCart(
                cartToken = holdToken,
                platformId = platformId,
                paymentReference = null,
                guestEmail = null,
                guestName = null,
                guestPhone = null
            )
        } returns BookingResponse(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            eventTitle = "event",
            eventDescription = null,
            sessionStartTime = Instant.now().toString(),
            sessionEndTime = Instant.now().toString(),
            customerEmail = "",
            customerName = "",
            items = emptyList(),
            totalPrice = app.venues.shared.money.MoneyAmount.zero("USD"),
            platformId = platformId,
            serviceFeeAmount = app.venues.shared.money.MoneyAmount.zero("USD"),
            discountAmount = app.venues.shared.money.MoneyAmount.zero("USD"),
            promoCode = null,
            status = app.venues.booking.api.domain.BookingStatus.CONFIRMED,
            confirmedAt = Instant.now().toString(),
            cancelledAt = null,
            cancellationReason = null,
            paymentId = null,
            createdAt = Instant.now().toString()
        )

        val response = service.confirm(
            platformId = platformId,
            request = PlatformConfirmRequest(
                holdToken = holdToken,
                paymentReference = null,
                guestEmail = null,
                guestName = null,
                guestPhone = null
            ),
        )

        assertNotNull(response.bookingId)
        assertEquals("USD", response.currency)
        verify {
            bookingApi.createBookingFromCart(
                cartToken = holdToken,
                platformId = platformId,
                paymentReference = null,
                guestEmail = null,
                guestName = null,
                guestPhone = null
            )
        }
    }
}

