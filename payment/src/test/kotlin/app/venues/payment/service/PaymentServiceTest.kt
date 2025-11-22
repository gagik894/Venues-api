package app.venues.payment.service

import app.venues.booking.api.BookingApi
import app.venues.finance.api.PaymentRoutingApi
import app.venues.finance.api.dto.MerchantProfileDto
import app.venues.finance.api.dto.PaymentConfig
import app.venues.finance.api.dto.TelcelConfig
import app.venues.payment.api.dto.InitiatePaymentRequest
import app.venues.payment.domain.Payment
import app.venues.payment.provider.PaymentProvider
import app.venues.payment.provider.PaymentProviderFactory
import app.venues.payment.provider.dto.PaymentLinkResult
import app.venues.payment.repository.PaymentRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.util.*

class PaymentServiceTest {

    private val paymentRepository = mockk<PaymentRepository>()
    private val paymentRoutingApi = mockk<PaymentRoutingApi>()
    private val paymentProviderFactory = mockk<PaymentProviderFactory>()
    private val bookingApi = mockk<BookingApi>(relaxed = true)

    private val paymentService = PaymentService(
        paymentRepository,
        paymentRoutingApi,
        paymentProviderFactory,
        bookingApi
    )

    @Test
    fun `initiatePayment should create payment and return response`() {
        // Given
        val venueId = UUID.randomUUID()
        val bookingId = UUID.randomUUID()
        val merchantId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()
        val amount = BigDecimal("100.00")
        val currency = "USD"
        val providerId = "telcel"

        val request = InitiatePaymentRequest(
            bookingId = bookingId,
            venueId = venueId,
            amount = amount,
            currency = currency,
            providerId = providerId
        )

        val telcelConfig = TelcelConfig(
            storeKey = "123",
            postponeBillIssuer = "issuer"
        )

        val config = PaymentConfig(
            telcel = telcelConfig
        )

        val merchant = MerchantProfileDto(
            id = merchantId,
            name = "Test Merchant",
            legalName = "Test Legal Name",
            taxId = "12345678",
            organizationId = organizationId,
            config = config
        )

        val provider = mockk<PaymentProvider>()
        val linkResult = PaymentLinkResult(
            paymentUrl = "http://test.com",
            gatewayReference = "ref-123",
            method = "GET"
        )

        every { paymentRoutingApi.resolveMerchant(venueId) } returns merchant
        every { paymentProviderFactory.getProvider(providerId) } returns provider
        every { provider.isConfigured(config) } returns true
        every { provider.generatePaymentLink(any(), config) } returns linkResult

        val paymentSlot = slot<Payment>()
        every { paymentRepository.save(capture(paymentSlot)) } answers { 
            paymentSlot.captured
        }

        // When
        val response = paymentService.initiatePayment(request)

        // Then
        assertEquals("PENDING", response.status)
        assertEquals("http://test.com", response.paymentUrl)
        assertEquals("ref-123", response.gatewayReference)

        val capturedPayment = paymentSlot.captured
        assertEquals(bookingId, capturedPayment.bookingId)
        assertEquals(amount, capturedPayment.amount)
        assertEquals(currency, capturedPayment.currency)
        assertEquals(merchantId, capturedPayment.merchantId)
        assertEquals("ref-123", capturedPayment.externalReference)

        verify { paymentRoutingApi.resolveMerchant(venueId) }
        verify { provider.generatePaymentLink(any(), config) }
        verify { paymentRepository.save(any()) }
    }

    @Test
    fun `initiatePayment should throw exception if provider not configured`() {
        // Given
        val venueId = UUID.randomUUID()
        val request = InitiatePaymentRequest(
            bookingId = UUID.randomUUID(),
            venueId = venueId,
            amount = BigDecimal("100.00"),
            currency = "USD",
            providerId = "telcel"
        )

        val telcelConfig = TelcelConfig("123")
        val config = PaymentConfig(telcel = telcelConfig)

        val merchant = MerchantProfileDto(
            id = UUID.randomUUID(),
            name = "Test Merchant", 
            legalName = null,
            taxId = null,
            organizationId = UUID.randomUUID(),
            config = config
        )
        val provider = mockk<PaymentProvider>()

        every { paymentRoutingApi.resolveMerchant(venueId) } returns merchant
        every { paymentProviderFactory.getProvider("telcel") } returns provider
        every { provider.providerId } returns "telcel"
        every { provider.isConfigured(config) } returns false

        // When/Then
        assertThrows<IllegalStateException> {
            paymentService.initiatePayment(request)
        }
    }
}
