package app.venues.platform.service

import app.venues.booking.api.BookingApi
import app.venues.booking.api.dto.BookingResponse
import app.venues.booking.api.dto.DirectSaleRequest
import app.venues.platform.domain.Platform
import app.venues.platform.repository.PlatformRepository
import app.venues.shared.money.MoneyAmount
import io.mockk.MockKAnnotations
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*

class PlatformDirectBookingTest {

    @MockK
    private lateinit var platformRepository: PlatformRepository
    @MockK
    private lateinit var cartApi: app.venues.booking.api.CartApi
    @MockK
    private lateinit var cartRepository: app.venues.booking.repository.CartRepository
    @MockK
    private lateinit var cartQueryApi: app.venues.booking.api.CartQueryApi
    @MockK
    private lateinit var bookingApi: BookingApi
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
            idempotencyService.withIdempotency<BookingResponse>(
                any(),
                any(),
                any(),
                BookingResponse::class.java,
                any()
            )
        } answers { lastArg<() -> BookingResponse>().invoke() }
    }

    @Test
    fun `direct booking delegates with platform rate limit`() {
        val platformId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val platform = Platform(
            name = "P",
            apiUrl = "https://p.com",
            sharedSecret = "secret",
            rateLimit = 25
        )

        every { platformRepository.findById(platformId) } returns Optional.of(platform)
        every { rateLimitService.enforce(platformId, 25) } returns Unit

        val bookingResponse = BookingResponse(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            eventTitle = "event",
            eventDescription = null,
            sessionStartTime = Instant.now().toString(),
            sessionEndTime = Instant.now().toString(),
            customerEmail = "c@e.com",
            customerName = "Cust",
            items = emptyList(),
            totalPrice = MoneyAmount.zero("USD"),
            serviceFeeAmount = MoneyAmount.zero("USD"),
            discountAmount = MoneyAmount.zero("USD"),
            promoCode = null,
            status = app.venues.booking.api.domain.BookingStatus.CONFIRMED,
            confirmedAt = Instant.now().toString(),
            cancelledAt = null,
            cancellationReason = null,
            paymentId = null,
            createdAt = Instant.now().toString()
        )

        every {
            bookingApi.createPlatformDirectBooking(
                request = any(),
                platformId = platformId,
                guestEmail = any(),
                guestName = any(),
                guestPhone = any(),
                confirmBooking = true
            )
        } returns bookingResponse

        val result = service.directBooking(
            platformId = platformId,
            request = DirectSaleRequest(
                sessionId = sessionId,
                customerEmail = "c@e.com",
                customerName = "Cust",
                items = listOf(app.venues.booking.api.dto.DirectSaleItemRequest(seatCode = "A1"))
            ),
            idempotencyKey = null
        )

        assertEquals("event", result.eventTitle)
        verify { rateLimitService.enforce(platformId, 25) }
    }

    @Test
    fun `direct booking accepts null guest info`() {
        val platformId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val platform = Platform(
            name = "P",
            apiUrl = "https://p.com",
            sharedSecret = "secret",
            rateLimit = 15
        )

        every { platformRepository.findById(platformId) } returns Optional.of(platform)
        every { rateLimitService.enforce(platformId, 15) } returns Unit

        val bookingResponse = BookingResponse(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            eventTitle = "event-null-guest",
            eventDescription = null,
            sessionStartTime = Instant.now().toString(),
            sessionEndTime = Instant.now().toString(),
            customerEmail = "",
            customerName = "",
            items = emptyList(),
            totalPrice = MoneyAmount.zero("USD"),
            serviceFeeAmount = MoneyAmount.zero("USD"),
            discountAmount = MoneyAmount.zero("USD"),
            promoCode = null,
            status = app.venues.booking.api.domain.BookingStatus.CONFIRMED,
            confirmedAt = Instant.now().toString(),
            cancelledAt = null,
            cancellationReason = null,
            paymentId = null,
            createdAt = Instant.now().toString()
        )

        every {
            bookingApi.createPlatformDirectBooking(
                request = any(),
                platformId = platformId,
                guestEmail = any(),
                guestName = any(),
                guestPhone = any(),
                confirmBooking = any()
            )
        } returns bookingResponse

        val result = service.directBooking(
            platformId = platformId,
            request = DirectSaleRequest(
                sessionId = sessionId,
                customerEmail = "",
                customerName = "",
                items = listOf(app.venues.booking.api.dto.DirectSaleItemRequest(seatCode = "A1"))
            ),
            idempotencyKey = null
        )

        assertEquals("event-null-guest", result.eventTitle)
        verify { rateLimitService.enforce(platformId, 15) }
    }
}

