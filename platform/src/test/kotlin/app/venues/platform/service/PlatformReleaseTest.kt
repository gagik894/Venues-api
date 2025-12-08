package app.venues.platform.service

import app.venues.booking.api.CartApi
import app.venues.booking.api.CartQueryApi
import app.venues.booking.api.dto.CartSummaryResponse
import app.venues.booking.repository.CartRepository
import app.venues.platform.api.dto.PlatformReleaseRequest
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

class PlatformReleaseTest {

    @MockK
    private lateinit var platformRepository: PlatformRepository
    @MockK
    private lateinit var cartApi: CartApi
    @MockK
    private lateinit var cartRepository: CartRepository
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
            idempotencyService.withIdempotency<app.venues.platform.api.dto.PlatformReleaseResponse>(
                any(), any(), any(),
                app.venues.platform.api.dto.PlatformReleaseResponse::class.java, any()
            )
        } answers { lastArg<() -> app.venues.platform.api.dto.PlatformReleaseResponse>().invoke() }
    }

    @Test
    fun `release delegates clearCart even for expired holds`() {
        val platformId = UUID.randomUUID()
        val platform = Platform(
            name = "P",
            apiUrl = "https://p.com",
            sharedSecret = "secret",
            rateLimit = 5
        )
        val cartToken = UUID.randomUUID()

        every { platformRepository.findById(platformId) } returns Optional.of(platform)
        every { rateLimitService.enforce(platformId, 5) } returns Unit

        every { cartApi.clearCart(cartToken, platformId) } returns CartSummaryResponse(
            token = cartToken,
            seats = emptyList(),
            gaItems = emptyList(),
            tables = emptyList(),
            totalPrice = MoneyAmount.zero("USD"),
            currency = "USD",
            expiresAt = "",
            sessionId = UUID.randomUUID(),
            eventTitle = "event"
        )

        val resp = service.release(
            platformId = platformId,
            request = PlatformReleaseRequest(reservationToken = cartToken),
            idempotencyKey = null
        )

        assertEquals(0, resp.releasedSeats)
        assertEquals(0, resp.releasedGATickets)
    }
}

