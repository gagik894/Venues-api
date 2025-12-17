package app.venues.venue.service

import app.venues.common.exception.VenuesException
import app.venues.location.domain.City
import app.venues.location.domain.Region
import app.venues.venue.api.dto.VenuePromoCodeRequest
import app.venues.venue.domain.DiscountType
import app.venues.venue.domain.Venue
import app.venues.venue.domain.VenuePromoCode
import app.venues.venue.repository.VenuePromoCodeRepository
import app.venues.venue.repository.VenueRepository
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class VenuePromoCodeServiceTest {

    private val promoCodeRepository: VenuePromoCodeRepository = mockk()
    private val venueRepository: VenueRepository = mockk()

    private lateinit var service: VenuePromoCodeService

    private val venueId = UUID.randomUUID()
    private val promoCodeId = UUID.randomUUID()
    private lateinit var testVenue: Venue

    @BeforeEach
    fun setup() {
        service = VenuePromoCodeService(promoCodeRepository, venueRepository)

        val region = Region(code = "AM-ER", names = mapOf("en" to "Yerevan"))
        val city = City(region = region, slug = "yerevan", names = mapOf("en" to "Yerevan"))
        testVenue = Venue(
            name = "Test Venue",
            description = "A test venue",
            slug = "test-venue",
            organizationId = UUID.randomUUID(),
            address = "123 Test St",
            city = city
        )
    }

    @Nested
    inner class CreatePromoCode {

        @Test
        fun `creates promo code successfully`() {
            val request = VenuePromoCodeRequest(
                code = "summer20",
                discountType = DiscountType.PERCENTAGE,
                discountValue = BigDecimal("20.00"),
                description = "Summer sale 20% off",
                minOrderAmount = BigDecimal("10000"),
                maxDiscountAmount = BigDecimal("5000"),
                maxUsageCount = 100,
                expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
            )

            every { venueRepository.findById(venueId) } returns Optional.of(testVenue)
            every { promoCodeRepository.existsByVenueIdAndCode(venueId, "SUMMER20") } returns false
            every { promoCodeRepository.save(any()) } answers {
                val code = firstArg<VenuePromoCode>()
                code.apply {
                    // Simulate ID assignment
                    val idField = VenuePromoCode::class.java.superclass.getDeclaredField("id")
                    idField.isAccessible = true
                    idField.set(this, promoCodeId)
                }
            }

            val result = service.createPromoCode(venueId, request)

            assertEquals("SUMMER20", result.code)
            assertEquals(DiscountType.PERCENTAGE, result.discountType)
            assertEquals("20.00", result.discountValue)
            verify { promoCodeRepository.save(match<VenuePromoCode> { it.code == "SUMMER20" }) }
        }

        @Test
        fun `normalizes code to uppercase`() {
            val request = VenuePromoCodeRequest(
                code = "MixedCase",
                discountType = DiscountType.FIXED_AMOUNT,
                discountValue = BigDecimal("1000"),
                description = null,
                minOrderAmount = null,
                maxDiscountAmount = null,
                maxUsageCount = null,
                expiresAt = null
            )

            every { venueRepository.findById(venueId) } returns Optional.of(testVenue)
            every { promoCodeRepository.existsByVenueIdAndCode(venueId, "MIXEDCASE") } returns false
            every { promoCodeRepository.save(any()) } answers { firstArg() }

            service.createPromoCode(venueId, request)

            verify { promoCodeRepository.existsByVenueIdAndCode(venueId, "MIXEDCASE") }
            verify { promoCodeRepository.save(match<VenuePromoCode> { it.code == "MIXEDCASE" }) }
        }

        @Test
        fun `throws ResourceConflict when code already exists`() {
            val request = VenuePromoCodeRequest(
                code = "EXISTING",
                discountType = DiscountType.PERCENTAGE,
                discountValue = BigDecimal("10"),
                description = null,
                minOrderAmount = null,
                maxDiscountAmount = null,
                maxUsageCount = null,
                expiresAt = null
            )

            every { venueRepository.findById(venueId) } returns Optional.of(testVenue)
            every { promoCodeRepository.existsByVenueIdAndCode(venueId, "EXISTING") } returns true

            val exception = assertThrows<VenuesException.ResourceConflict> {
                service.createPromoCode(venueId, request)
            }

            assertTrue(exception.message!!.contains("already exists"))
        }

        @Test
        fun `throws ResourceNotFound when venue not found`() {
            val request = VenuePromoCodeRequest(
                code = "TEST",
                discountType = DiscountType.PERCENTAGE,
                discountValue = BigDecimal("10"),
                description = null,
                minOrderAmount = null,
                maxDiscountAmount = null,
                maxUsageCount = null,
                expiresAt = null
            )

            every { venueRepository.findById(venueId) } returns Optional.empty()

            assertThrows<VenuesException.ResourceNotFound> {
                service.createPromoCode(venueId, request)
            }
        }
    }

    @Nested
    inner class UpdatePromoCode {

        @Test
        fun `updates promo code successfully`() {
            val existingCode = VenuePromoCode(
                venue = testVenue,
                code = "OLD",
                discountType = DiscountType.PERCENTAGE,
                discountValue = BigDecimal("10"),
                description = null,
                minOrderAmount = null,
                maxDiscountAmount = null,
                maxUsageCount = null,
                expiresAt = null
            )
            val request = VenuePromoCodeRequest(
                code = "NEW",
                discountType = DiscountType.FIXED_AMOUNT,
                discountValue = BigDecimal("5000"),
                description = "Updated description",
                minOrderAmount = null,
                maxDiscountAmount = null,
                maxUsageCount = 50,
                expiresAt = null
            )

            every { promoCodeRepository.findByIdAndVenueId(promoCodeId, venueId) } returns Optional.of(existingCode)
            every { promoCodeRepository.existsByVenueIdAndCode(venueId, "NEW") } returns false
            every { promoCodeRepository.save(any()) } answers { firstArg() }

            val result = service.updatePromoCode(venueId, promoCodeId, request)

            assertEquals("NEW", result.code)
            assertEquals(DiscountType.FIXED_AMOUNT, result.discountType)
        }

        @Test
        fun `throws ValidationFailure when reducing max usage below current usage`() {
            val existingCode = mockk<VenuePromoCode>(relaxed = true) {
                every { code } returns "TEST"
                every { currentUsageCount } returns 50
            }
            val request = VenuePromoCodeRequest(
                code = "TEST",
                discountType = DiscountType.PERCENTAGE,
                discountValue = BigDecimal("10"),
                description = null,
                minOrderAmount = null,
                maxDiscountAmount = null,
                maxUsageCount = 30, // Less than current usage of 50
                expiresAt = null
            )

            every { promoCodeRepository.findByIdAndVenueId(promoCodeId, venueId) } returns Optional.of(existingCode)

            val exception = assertThrows<VenuesException.ValidationFailure> {
                service.updatePromoCode(venueId, promoCodeId, request)
            }

            assertTrue(exception.message!!.contains("already been used"))
        }

        @Test
        fun `throws ResourceNotFound when promo code not found`() {
            val request = VenuePromoCodeRequest(
                code = "TEST",
                discountType = DiscountType.PERCENTAGE,
                discountValue = BigDecimal("10"),
                description = null,
                minOrderAmount = null,
                maxDiscountAmount = null,
                maxUsageCount = null,
                expiresAt = null
            )

            every { promoCodeRepository.findByIdAndVenueId(promoCodeId, venueId) } returns Optional.empty()

            assertThrows<VenuesException.ResourceNotFound> {
                service.updatePromoCode(venueId, promoCodeId, request)
            }
        }
    }

    @Nested
    inner class GetPromoCodes {

        @Test
        fun `returns all promo codes when no search`() {
            val codes = listOf(
                mockk<VenuePromoCode>(relaxed = true) {
                    every { id } returns UUID.randomUUID()
                    every { code } returns "CODE1"
                    every { discountType } returns DiscountType.PERCENTAGE
                    every { discountValue } returns BigDecimal("10")
                    every { isActive } returns true
                },
                mockk<VenuePromoCode>(relaxed = true) {
                    every { id } returns UUID.randomUUID()
                    every { code } returns "CODE2"
                    every { discountType } returns DiscountType.FIXED_AMOUNT
                    every { discountValue } returns BigDecimal("5000")
                    every { isActive } returns true
                }
            )

            every { promoCodeRepository.findByVenueId(venueId) } returns codes

            val result = service.getPromoCodes(venueId, null)

            assertEquals(2, result.size)
        }

        @Test
        fun `returns filtered promo codes when search provided`() {
            val codes = listOf(
                mockk<VenuePromoCode>(relaxed = true) {
                    every { id } returns UUID.randomUUID()
                    every { code } returns "SUMMER20"
                    every { discountType } returns DiscountType.PERCENTAGE
                    every { discountValue } returns BigDecimal("20")
                    every { isActive } returns true
                }
            )

            every { promoCodeRepository.findByVenueIdAndCodeContainingIgnoreCase(venueId, "summer") } returns codes

            val result = service.getPromoCodes(venueId, "summer")

            assertEquals(1, result.size)
            assertEquals("SUMMER20", result[0].code)
        }
    }

    @Nested
    inner class DeactivatePromoCode {

        @Test
        fun `deactivates promo code successfully`() {
            val code = VenuePromoCode(
                venue = testVenue,
                code = "TEST",
                discountType = DiscountType.PERCENTAGE,
                discountValue = BigDecimal("10"),
                description = null,
                minOrderAmount = null,
                maxDiscountAmount = null,
                maxUsageCount = null,
                expiresAt = null
            )

            every { promoCodeRepository.findByIdAndVenueId(promoCodeId, venueId) } returns Optional.of(code)
            every { promoCodeRepository.save(any()) } answers { firstArg() }

            service.deactivatePromoCode(venueId, promoCodeId)

            assertFalse(code.isActive)
            verify { promoCodeRepository.save(code) }
        }
    }

    @Nested
    inner class ValidatePromoCode {

        @Test
        fun `returns valid promo code`() {
            val code = VenuePromoCode(
                venue = testVenue,
                code = "VALID",
                discountType = DiscountType.PERCENTAGE,
                discountValue = BigDecimal("10"),
                description = null,
                minOrderAmount = null,
                maxDiscountAmount = null,
                maxUsageCount = null,
                expiresAt = null
            )

            every {
                promoCodeRepository.findValidPromoCodeByVenueIdAndCode(
                    venueId,
                    "VALID",
                    any()
                )
            } returns Optional.of(code)

            val result = service.validatePromoCode(venueId, "valid")

            assertEquals("VALID", result.code)
        }

        @Test
        fun `throws ResourceNotFound for invalid code`() {
            every {
                promoCodeRepository.findValidPromoCodeByVenueIdAndCode(
                    venueId,
                    "INVALID",
                    any()
                )
            } returns Optional.empty()

            assertThrows<VenuesException.ResourceNotFound> {
                service.validatePromoCode(venueId, "invalid")
            }
        }
    }

    @Nested
    inner class ReservePromoCode {

        @Test
        fun `reserves promo code successfully`() {
            every { promoCodeRepository.incrementUsageIfAllowed(venueId, "TEST", any()) } returns 1

            service.reservePromoCode(venueId, "test")

            verify { promoCodeRepository.incrementUsageIfAllowed(venueId, "TEST", any()) }
        }

        @Test
        fun `throws ResourceNotFound when reservation fails`() {
            every { promoCodeRepository.incrementUsageIfAllowed(venueId, "TEST", any()) } returns 0

            assertThrows<VenuesException.ResourceNotFound> {
                service.reservePromoCode(venueId, "test")
            }
        }
    }

    @Nested
    inner class ReleasePromoCode {

        @Test
        fun `releases promo code successfully`() {
            every { promoCodeRepository.decrementUsage(venueId, "TEST") } just runs

            service.releasePromoCode(venueId, "test")

            verify { promoCodeRepository.decrementUsage(venueId, "TEST") }
        }
    }
}
