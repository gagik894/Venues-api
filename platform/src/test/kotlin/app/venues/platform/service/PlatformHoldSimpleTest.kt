package app.venues.platform.service

import app.venues.booking.api.CartApi
import app.venues.booking.api.CartQueryApi
import app.venues.booking.api.dto.CartSummaryResponse
import app.venues.booking.api.dto.PlatformHoldBatchRequest
import app.venues.platform.api.dto.PlatformGAReservation
import app.venues.platform.api.dto.PlatformHoldRequest
import app.venues.platform.api.dto.PlatformHoldResponse
import app.venues.platform.domain.Platform
import app.venues.platform.repository.PlatformRepository
import app.venues.shared.money.MoneyAmount
import io.mockk.MockKAnnotations
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

class PlatformHoldSimpleTest {

    @MockK
    private lateinit var platformRepository: PlatformRepository
    @MockK
    private lateinit var cartApi: CartApi
    @MockK
    private lateinit var cartRepository: app.venues.booking.repository.CartRepository
    @MockK
    private lateinit var cartQueryApi: CartQueryApi
    @MockK
    private lateinit var bookingApi: app.venues.booking.api.BookingApi
    @MockK
    private lateinit var rateLimitService: PlatformRateLimitService
    @MockK
    private lateinit var idempotencyService: PlatformIdempotencyService

    private lateinit var service: PlatformBookingService

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        clearMocks(
            platformRepository,
            cartApi,
            cartRepository,
            cartQueryApi,
            bookingApi,
            rateLimitService,
            idempotencyService
        )
        service = PlatformBookingService(
            platformRepository,
            cartApi,
            cartRepository,
            cartQueryApi,
            bookingApi,
            rateLimitService,
            idempotencyService
        )

        every {
            idempotencyService.withIdempotency<PlatformHoldResponse>(
                any(),
                any(),
                any(),
                PlatformHoldResponse::class.java,
                any()
            )
        } answers { lastArg<() -> PlatformHoldResponse>().invoke() }
    }

    @Test
    fun `holdSimple delegates to booking batch hold with ttl`() {
        val platformId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val platform = Platform(
            name = "P",
            apiUrl = "https://p.com",
            sharedSecret = "secret",
            rateLimit = 50
        )

        every { platformRepository.findById(platformId) } returns Optional.of(platform)
        every { rateLimitService.enforce(platformId, 50) } returns Unit

        every {
            cartApi.holdBatch(any())
        } answers {
            val req = firstArg<PlatformHoldBatchRequest>()
            assertEquals(60L, req.ttlSeconds)
            CartSummaryResponse(
                token = UUID.randomUUID(),
                seats = emptyList(),
                gaItems = emptyList(),
                tables = emptyList(),
                totalPrice = MoneyAmount.zero("USD"),
                currency = "USD",
                expiresAt = "2025-01-01T00:00:00Z",
                sessionId = sessionId,
                eventTitle = "event"
            )
        }

        val response = service.holdSimple(
            platformId = platformId,
            request = PlatformHoldRequest(
                sessionId = sessionId,
                seatIdentifiers = listOf("A1"),
                gaReservations = listOf(PlatformGAReservation("GA1", 2)),
                ttlSeconds = 60L
            ),
            idempotencyKey = null
        )

        assertEquals("USD", response.currency)
    }
}

