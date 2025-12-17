package app.venues.booking.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.util.*

/**
 * Unit tests for Booking entity state machine transitions.
 * Tests isConfirmable, isCancellable, confirm, and cancel state logic.
 */
class BookingStateMachineTest {

    private val sessionId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    // ===========================================
    // CONFIRMABLE STATE TESTS
    // ===========================================

    @Test
    fun `new booking is confirmable`() {
        val booking = createBooking()
        assertTrue(booking.isConfirmable())
    }

    @Test
    fun `confirm succeeds for new PENDING booking`() {
        val booking = createBooking()
        assertTrue(booking.isConfirmable())

        assertDoesNotThrow {
            booking.confirm(UUID.randomUUID())
        }

        assertFalse(booking.isConfirmable())
    }

    @Test
    fun `confirm throws IllegalArgumentException for already CONFIRMED booking`() {
        val booking = createBooking()
        booking.confirm(UUID.randomUUID())

        val exception = assertThrows<IllegalArgumentException> {
            booking.confirm(UUID.randomUUID())
        }

        assertTrue(exception.message?.contains("PENDING") == true)
    }

    // ===========================================
    // CANCELLABLE STATE TESTS
    // ===========================================

    @Test
    fun `new booking is cancellable`() {
        val booking = createBooking()
        assertTrue(booking.isCancellable())
    }

    @Test
    fun `confirmed booking is cancellable`() {
        val booking = createBooking()
        booking.confirm(UUID.randomUUID())
        assertTrue(booking.isCancellable())
    }

    @Test
    fun `cancel succeeds for PENDING booking`() {
        val booking = createBooking()
        assertTrue(booking.isCancellable())

        assertDoesNotThrow {
            booking.cancel("Test cancellation")
        }

        assertFalse(booking.isCancellable())
    }

    @Test
    fun `cancel succeeds for CONFIRMED booking`() {
        val booking = createBooking()
        booking.confirm(UUID.randomUUID())
        assertTrue(booking.isCancellable())

        assertDoesNotThrow {
            booking.cancel("Test cancellation")
        }

        assertFalse(booking.isCancellable())
    }

    @Test
    fun `cancel throws IllegalArgumentException for already CANCELLED booking`() {
        val booking = createBooking()
        booking.cancel("First cancellation")

        val exception = assertThrows<IllegalArgumentException> {
            booking.cancel("Second cancellation")
        }

        assertTrue(exception.message?.contains("CANCELLED") == true || exception.message?.contains("PENDING or CONFIRMED") == true)
    }

    // ===========================================
    // STATE TRANSITION SEQUENCES
    // ===========================================

    @Test
    fun `valid state sequence PENDING to CONFIRMED`() {
        val booking = createBooking()

        // New booking is PENDING
        assertTrue(booking.isConfirmable())
        assertTrue(booking.isCancellable())

        // Confirm it
        booking.confirm(UUID.randomUUID())

        // Now it's CONFIRMED
        assertFalse(booking.isConfirmable())
        assertTrue(booking.isCancellable())
    }

    @Test
    fun `valid state sequence PENDING to CANCELLED`() {
        val booking = createBooking()

        // New booking is PENDING
        assertTrue(booking.isConfirmable())
        assertTrue(booking.isCancellable())

        // Cancel it
        booking.cancel("User request")

        // Now it's CANCELLED
        assertFalse(booking.isConfirmable())
        assertFalse(booking.isCancellable())
    }

    @Test
    fun `valid state sequence CONFIRMED to CANCELLED`() {
        val booking = createBooking()

        // Confirm first
        booking.confirm(UUID.randomUUID())
        assertFalse(booking.isConfirmable())
        assertTrue(booking.isCancellable())

        // Then cancel
        booking.cancel("Refund request")
        assertFalse(booking.isConfirmable())
        assertFalse(booking.isCancellable())
    }

    @Test
    fun `cannot confirm twice`() {
        val booking = createBooking()

        booking.confirm(UUID.randomUUID())

        assertThrows<IllegalArgumentException> {
            booking.confirm(UUID.randomUUID())
        }
    }

    @Test
    fun `cannot cancel twice`() {
        val booking = createBooking()

        booking.cancel("First cancellation")

        assertThrows<IllegalArgumentException> {
            booking.cancel("Second cancellation")
        }
    }

    // ===========================================
    // EDGE CASES
    // ===========================================

    @Test
    fun `state methods are idempotent checks`() {
        val booking = createBooking()

        // Multiple calls to isConfirmable don't change state
        assertTrue(booking.isConfirmable())
        assertTrue(booking.isConfirmable())
        assertTrue(booking.isConfirmable())

        // Booking is still confirmable
        assertTrue(booking.isConfirmable())
    }

    @Test
    fun `confirm and cancel are mutually exclusive on PENDING`() {
        val booking1 = createBooking()
        booking1.confirm(UUID.randomUUID())
        assertFalse(booking1.isConfirmable())
        assertTrue(booking1.isCancellable())

        val booking2 = createBooking()
        booking2.cancel("Test")
        assertFalse(booking2.isConfirmable())
        assertFalse(booking2.isCancellable())
    }

    @Test
    fun `cannot confirm after cancellation`() {
        val booking = createBooking()
        booking.cancel("Cancelled first")

        assertFalse(booking.isConfirmable())
        assertThrows<IllegalArgumentException> {
            booking.confirm(UUID.randomUUID())
        }
    }

    // ===========================================
    // HELPER METHODS
    // ===========================================

    private fun createBooking(): Booking {
        return Booking(
            userId = userId,
            guest = null,
            sessionId = sessionId,
            totalPrice = BigDecimal("100.00"),
            currency = "AMD",
            salesChannel = SalesChannel.WEBSITE,
            platformId = null,
            staffId = null,
            venueId = UUID.randomUUID(),
            externalOrderNumber = null,
            serviceFeeAmount = BigDecimal.ZERO,
            discountAmount = BigDecimal.ZERO,
            promoCode = null,
            version = 0
        )
    }
}
