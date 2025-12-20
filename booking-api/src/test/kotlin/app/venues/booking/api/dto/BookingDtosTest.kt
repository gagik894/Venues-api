package app.venues.booking.api.dto

import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.*

class BookingDtosTest {

    companion object {
        private lateinit var validator: Validator

        @JvmStatic
        @BeforeAll
        fun setUp() {
            val factory = Validation.buildDefaultValidatorFactory()
            validator = factory.validator
        }
    }

    @Test
    fun `CheckoutRequest should pass validation with valid data`() {
        val request = CheckoutRequest(
            token = UUID.randomUUID(),
            email = "test@example.com",
            name = "John Doe",
            phone = "1234567890"
        )

        val violations = validator.validate(request)
        assertTrue(violations.isEmpty(), "Expected no validation errors")
    }

    @Test
    fun `CheckoutRequest should fail validation with invalid email`() {
        val request = CheckoutRequest(
            email = "invalid-email",
            name = "John Doe"
        )

        val violations = validator.validate(request)
        assertEquals(1, violations.size)
        assertEquals("Invalid email format", violations.first().message)
    }

    @Test
    fun `CheckoutRequest should fail validation with blank name`() {
        val request = CheckoutRequest(
            email = "test@example.com",
            name = ""
        )

        val violations = validator.validate(request)
        assertEquals(1, violations.size)
        assertEquals("Name is required", violations.first().message)
    }

    @Test
    fun `CheckoutRequest should fail validation with too long name`() {
        val longName = "a".repeat(201)
        val request = CheckoutRequest(
            email = "test@example.com",
            name = longName
        )

        val violations = validator.validate(request)
        assertEquals(1, violations.size)
        assertEquals("Name must not exceed 200 characters", violations.first().message)
    }

    @Test
    fun `ConfirmBookingRequest should pass validation with valid data`() {
        val request = ConfirmBookingRequest(
            paymentId = UUID.randomUUID()
        )
        val violations = validator.validate(request)
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `CancelBookingRequest should fail validation with too long reason`() {
        val longReason = "a".repeat(501)
        val request = CancelBookingRequest(
            reason = longReason
        )

        val violations = validator.validate(request)
        assertEquals(1, violations.size)
        assertEquals("Reason must not exceed 500 characters", violations.first().message)
    }
}
