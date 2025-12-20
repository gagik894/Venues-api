package app.venues.booking.domain

import app.venues.booking.domain.Cart
import app.venues.booking.domain.CartItem
import app.venues.booking.domain.CartSeat
import app.venues.booking.domain.CartTable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.*

class CartTest {

    private val sessionId = UUID.randomUUID()

    @Test
    fun `extendExpiration adds time correctly`() {
        val now = Instant.now()
        val cart = Cart(
            sessionId = sessionId,
            expiresAt = now.plusSeconds(300), // 5 mins left
            token = UUID.randomUUID()
        ).apply { createdAt = now }

        // Extend by 5 mins, cap at 20 mins initial, 20 mins max TTL
        cart.extendExpiration(5, 20, 20)

        // Should be roughly 10 mins from now
        val remainingSeconds = cart.expiresAt.epochSecond - now.epochSecond
        assertTrue(remainingSeconds in 599..601, "Expected ~600 seconds remaining, got $remainingSeconds")
    }

    @Test
    fun `extendExpiration caps at initial expiration limit from now`() {
        val now = Instant.now()
        val cart = Cart(
            sessionId = sessionId,
            expiresAt = now.plusSeconds(300), // 5 mins left
            token = UUID.randomUUID()
        ).apply { createdAt = now }

        // Try to extend by 100 mins, cap at 10 mins from NOW
        cart.extendExpiration(100, 10, 30)

        val remainingSeconds = cart.expiresAt.epochSecond - now.epochSecond
        assertTrue(remainingSeconds in 599..601, "Expected capped at 600 seconds, got $remainingSeconds")
    }

    @Test
    fun `extendExpiration caps at max TTL from creation`() {
        val now = Instant.now()
        val cart = Cart(
            sessionId = sessionId,
            expiresAt = now.plusSeconds(300), // 5 mins left
            token = UUID.randomUUID()
        ).apply { createdAt = now.minusSeconds(1000) } // Created 1000s ago

        // Max TTL = 20 mins = 1200s
        // Created 1000s ago means we have 200s left of total life.
        // Current expiration is 300s from now (which is actually impossible if maxTTL was enforced strictly before but let's say it was extended).
        
        // Let's make a cleaner scenario
        // Created 15 mins ago (900s). Max TTL 20 mins (1200s). Remaining TTL budget = 300s.
        // Current expiresAt = now + 60s.
        // Extend by 10 mins (600s).
        // Cap 1 (Initial form now): 20 mins (1200s). 60+600 = 660s. OK.
        // Cap 2 (Max TTL): 1200s - 900s = 300s.
        // Should be capped at 300s.

        val oldCreation = now.minusSeconds(900)
        val testCart = Cart(
            sessionId = sessionId,
            expiresAt = now.plusSeconds(60),
            token = UUID.randomUUID()
        ).apply { createdAt = oldCreation }

        testCart.extendExpiration(10, 20, 20)
        
        val remaining = testCart.expiresAt.epochSecond - now.epochSecond
        assertTrue(remaining in 299..301, "Expected capped at 300 seconds (Max TTL), got $remaining")
    }

    @Test
    fun `getTotalPrice sums items and applies discount`() {
        val cart = Cart(
            sessionId = sessionId,
            expiresAt = Instant.now(),
            token = UUID.randomUUID(),
            discountAmount = BigDecimal("10.00")
        )

        // Add Seat: 50
        cart.seats.add(CartSeat(cart, sessionId, 1L, BigDecimal("50.00")))
        // Add GA: 2 * 20 = 40
        cart.gaItems.add(CartItem(cart, sessionId, 2L, BigDecimal("20.00"), 2))
        // Add Table: 100
        cart.tables.add(CartTable(cart, sessionId, 3L, BigDecimal("100.00")))

        // Subtotal = 50 + 40 + 100 = 190
        // Discount 10
        // Total = 180

        assertEquals(BigDecimal("180.00"), cart.getTotalPrice())
    }

    @Test
    fun `getTotalPrice returns zero if discount exceeds total`() {
        val cart = Cart(
            sessionId = sessionId,
            expiresAt = Instant.now(),
            token = UUID.randomUUID(),
            discountAmount = BigDecimal("1000.00")
        )

        cart.seats.add(CartSeat(cart, sessionId, 1L, BigDecimal("50.00")))

        assertEquals(BigDecimal.ZERO, cart.getTotalPrice())
    }

    @Test
    fun `getItemCount counts distinct items`() {
        val cart = Cart(sessionId = sessionId, expiresAt = Instant.now(), token = UUID.randomUUID())
        cart.seats.add(CartSeat(cart, sessionId, 1L, BigDecimal.TEN))
        cart.gaItems.add(CartItem(cart, sessionId, 2L, BigDecimal.TEN, 5)) // 1 item, qty 5
        
        assertEquals(2, cart.getItemCount())
    }
    
    @Test
    fun `getTotalTicketCount counts quantities`() {
        val cart = Cart(sessionId = sessionId, expiresAt = Instant.now(), token = UUID.randomUUID())
        cart.seats.add(CartSeat(cart, sessionId, 1L, BigDecimal.TEN)) // 1
        cart.gaItems.add(CartItem(cart, sessionId, 2L, BigDecimal.TEN, 5)) // 5
        cart.tables.add(CartTable(cart, sessionId, 3L, BigDecimal.TEN)) // 1 (Table count)
        
        assertEquals(7, cart.getTotalTicketCount())
    }
}
