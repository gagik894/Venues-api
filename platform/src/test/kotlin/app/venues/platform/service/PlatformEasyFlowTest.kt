package app.venues.platform.service

import app.venues.booking.api.BookingApi
import app.venues.booking.api.CartApi
import app.venues.booking.api.CartQueryApi
import app.venues.booking.api.CartValidationApi
import app.venues.booking.api.dto.BookingResponse
import app.venues.booking.api.dto.DirectSaleRequest
import app.venues.common.exception.VenuesException
import app.venues.platform.api.dto.PlatformEasyItemRequest
import app.venues.platform.api.dto.PlatformEasyReserveRequest
import app.venues.platform.domain.Platform
import app.venues.platform.repository.PlatformRepository
import app.venues.shared.money.MoneyAmount
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

class PlatformEasyFlowTest {

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
    @MockK
    private lateinit var idempotencyService: PlatformIdempotencyService

    private lateinit var service: PlatformBookingService
    private val platformId = UUID.randomUUID()
    private val activePlatform = Platform(
        name = "platform",
        apiUrl = "https://example.com",
        sharedSecret = "secret"
    )

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        service = PlatformBookingService(
            platformRepository,
            cartApi,
            cartQueryApi,
            cartValidationApi,
            bookingApi,
            rateLimitService,
            idempotencyService
        )

        every { platformRepository.findById(platformId) } returns java.util.Optional.of(activePlatform)
        every { rateLimitService.enforce(any(), any()) } just Runs
        every {
            idempotencyService.withIdempotency(
                idempotencyKey = any(),
                platformId = any(),
                endpoint = any(),
                responseType = BookingResponse::class.java,
                supplier = any()
            )
        } answers { (arg<() -> BookingResponse>(4))() }
    }

    @Test
    fun `reserveSimple maps easy request and calls booking API with confirm false`() {
        val sessionId = UUID.randomUUID()
        val bookingResponse = BookingResponse(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            eventTitle = "event",
            eventDescription = null,
            sessionStartTime = "t1",
            sessionEndTime = "t2",
            customerEmail = "c@e.com",
            customerName = "Cust",
            items = emptyList(),
            totalPrice = MoneyAmount.zero("USD"),
            platformId = platformId,
            serviceFeeAmount = MoneyAmount.zero("USD"),
            discountAmount = MoneyAmount.zero("USD"),
            promoCode = null,
            status = app.venues.booking.api.domain.BookingStatus.PENDING,
            confirmedAt = null,
            cancelledAt = null,
            cancellationReason = null,
            paymentId = null,
            createdAt = "now"
        )

        every {
            bookingApi.createPlatformDirectBooking(
                request = any(),
                platformId = platformId,
                guestEmail = any(),
                guestName = any(),
                guestPhone = any(),
                confirmBooking = false
            )
        } returns bookingResponse

        val request = PlatformEasyReserveRequest(
            sessionId = sessionId,
            customerEmail = "c@e.com",
            customerName = "Cust",
            customerPhone = "123",
            items = listOf(PlatformEasyItemRequest(seatCode = "S1")),
            promoCode = "PROMO"
        )

        val result = service.reserveSimple(platformId, request, idempotencyKey = "idemp-1")

        assertEquals(bookingResponse.id, result.id)
        verify {
            bookingApi.createPlatformDirectBooking(
                request = withArg<DirectSaleRequest> {
                    assertEquals(request.sessionId, it.sessionId)
                    assertEquals(request.customerEmail, it.customerEmail)
                    assertEquals(request.customerName, it.customerName)
                    assertEquals(request.customerPhone, it.customerPhone)
                    assertEquals(request.promoCode, it.promoCode)
                    assertEquals(1, it.items.size)
                    assertEquals("S1", it.items.first().seatCode)
                },
                platformId = platformId,
                guestEmail = any(),
                guestName = any(),
                guestPhone = any(),
                confirmBooking = false
            )
        }
    }

    @Test
    fun `confirmSimple rejects booking from another platform`() {
        val bookingId = UUID.randomUUID()
        every { bookingApi.getBookingById(bookingId) } returns BookingResponse(
            id = bookingId.toString(),
            sessionId = UUID.randomUUID(),
            eventTitle = "event",
            eventDescription = null,
            sessionStartTime = "t1",
            sessionEndTime = "t2",
            customerEmail = "c@e.com",
            customerName = "Cust",
            items = emptyList(),
            totalPrice = MoneyAmount.zero("USD"),
            platformId = UUID.randomUUID(), // mismatch
            serviceFeeAmount = MoneyAmount.zero("USD"),
            discountAmount = MoneyAmount.zero("USD"),
            promoCode = null,
            status = app.venues.booking.api.domain.BookingStatus.PENDING,
            confirmedAt = null,
            cancelledAt = null,
            cancellationReason = null,
            paymentId = null,
            createdAt = "now"
        )

        assertThrows(VenuesException.AuthorizationFailure::class.java) {
            service.confirmSimple(platformId, bookingId, idempotencyKey = "idemp-2")
        }
        verify(exactly = 0) { bookingApi.confirmPlatformBooking(any()) }
    }

    @Test
    fun `releaseSimple rejects booking from another platform`() {
        val bookingId = UUID.randomUUID()
        every { bookingApi.getBookingById(bookingId) } returns BookingResponse(
            id = bookingId.toString(),
            sessionId = UUID.randomUUID(),
            eventTitle = "event",
            eventDescription = null,
            sessionStartTime = "t1",
            sessionEndTime = "t2",
            customerEmail = "c@e.com",
            customerName = "Cust",
            items = emptyList(),
            totalPrice = MoneyAmount.zero("USD"),
            platformId = UUID.randomUUID(), // mismatch
            serviceFeeAmount = MoneyAmount.zero("USD"),
            discountAmount = MoneyAmount.zero("USD"),
            promoCode = null,
            status = app.venues.booking.api.domain.BookingStatus.PENDING,
            confirmedAt = null,
            cancelledAt = null,
            cancellationReason = null,
            paymentId = null,
            createdAt = "now"
        )

        assertThrows(VenuesException.AuthorizationFailure::class.java) {
            service.releaseSimple(platformId, bookingId, idempotencyKey = "idemp-3")
        }
        verify(exactly = 0) { bookingApi.cancelBooking(any()) }
    }
}

