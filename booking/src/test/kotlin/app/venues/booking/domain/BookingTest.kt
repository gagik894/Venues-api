package app.venues.booking.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.*

class BookingTest {

    private val sessionId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    @Test
    fun `applyServiceFeePercent calculates fee correctly`() {
        val booking = createBooking()
        
        // Base 100, 2.5% fee
        // Fee = 2.50
        // Total = 102.50
        booking.applyServiceFeePercent(BigDecimal("100.00"), BigDecimal("2.5"))
        
        assertEquals(BigDecimal("2.50"), booking.serviceFeeAmount)
        assertEquals(BigDecimal("102.50"), booking.totalPrice)
    }

    @Test
    fun `applyServiceFeePercent handles scaling`() {
        val booking = createBooking()
        
        // Base 10, 33.33333333333% fee
        // 10 * 0.33333... = 3.3333 -> 3.33
        booking.applyServiceFeePercent(BigDecimal("10.00"), BigDecimal("33.333333333"))

        assertEquals(BigDecimal("3.33"), booking.serviceFeeAmount)
        assertEquals(BigDecimal("13.33"), booking.totalPrice)
    }

    @Test
    fun `applyServiceFeePercent throws on negative percent`() {
        val booking = createBooking()
        assertThrows(IllegalArgumentException::class.java) {
            booking.applyServiceFeePercent(BigDecimal("100"), BigDecimal("-1"))
        }
    }

    @Test
    fun `addItem associates item with booking`() {
        val booking = createBooking()
        val item = BookingItem(
            booking = booking,
            quantity = 1,
            unitPrice = BigDecimal.TEN,
            seatId = 1L,
            priceTemplateName = "Standard"
        )
        
        booking.addItem(item)
        
        assertEquals(1, booking.items.size)
        assertEquals(booking, item.booking)
    }

    private fun createBooking(): Booking {
        return Booking(
            userId = userId,
            guest = null,
            sessionId = sessionId,
            totalPrice = BigDecimal.ZERO,
            currency = "AMD",
            salesChannel = SalesChannel.WEBSITE,
            platformId = null,
            staffId = null,
            venueId = UUID.randomUUID()
        )
    }
}
