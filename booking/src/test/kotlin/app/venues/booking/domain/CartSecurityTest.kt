package app.venues.booking.domain

import app.venues.common.exception.VenuesException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.*

/**
 * Unit tests for Cart entity security features.
 *
 * Tests verify:
 * - Platform ownership validation (CRIT-01, CRIT-02 audit fixes)
 * - isPlatformCart detection
 * - Cart expiration logic
 * - isEmpty and getItemCount behavior
 */
class CartSecurityTest {

    private val sessionId = UUID.randomUUID()

    // ===========================================
    // PLATFORM OWNERSHIP VALIDATION TESTS
    // ===========================================

    @Test
    fun `validatePlatformOwnership succeeds when platformId matches`() {
        val platformId = UUID.randomUUID()
        val cart = createCart(platformId = platformId)

        assertDoesNotThrow {
            cart.validatePlatformOwnership(platformId)
        }
    }

    @Test
    fun `validatePlatformOwnership throws AuthorizationFailure when platformId differs`() {
        val platformId = UUID.randomUUID()
        val otherPlatformId = UUID.randomUUID()
        val cart = createCart(platformId = platformId)

        val exception = assertThrows<VenuesException.AuthorizationFailure> {
            cart.validatePlatformOwnership(otherPlatformId)
        }

        assertTrue(exception.message?.contains("different platform") == true)
    }

    @Test
    fun `validatePlatformOwnership throws ValidationFailure when cart has no platformId`() {
        val cart = createCart(platformId = null)
        val requestingPlatformId = UUID.randomUUID()

        val exception = assertThrows<VenuesException.ValidationFailure> {
            cart.validatePlatformOwnership(requestingPlatformId)
        }

        assertTrue(exception.message?.contains("not a platform reservation") == true)
    }

    // ===========================================
    // IS PLATFORM CART TESTS
    // ===========================================

    @Test
    fun `isPlatformCart returns true when platformId is set`() {
        val cart = createCart(platformId = UUID.randomUUID())
        assertTrue(cart.isPlatformCart())
    }

    @Test
    fun `isPlatformCart returns false when platformId is null`() {
        val cart = createCart(platformId = null)
        assertFalse(cart.isPlatformCart())
    }

    // ===========================================
    // EXPIRATION TESTS
    // ===========================================

    @Test
    fun `isExpired returns false for future expiration`() {
        val cart = createCart(expiresAt = Instant.now().plusSeconds(300))
        assertFalse(cart.isExpired())
    }

    @Test
    fun `isExpired returns true for past expiration`() {
        val cart = createCart(expiresAt = Instant.now().minusSeconds(60))
        assertTrue(cart.isExpired())
    }

    @Test
    fun `isExpired returns true for exactly now`() {
        // Note: Due to timing, this test creates a cart that expires 1ms ago
        val cart = createCart(expiresAt = Instant.now().minusMillis(1))
        assertTrue(cart.isExpired())
    }

    // ===========================================
    // EMPTY CART TESTS
    // ===========================================

    @Test
    fun `isEmpty returns true for new cart`() {
        val cart = createCart()
        assertTrue(cart.isEmpty())
    }

    @Test
    fun `getItemCount returns zero for new cart`() {
        val cart = createCart()
        assertEquals(0, cart.getItemCount())
    }

    @Test
    fun `getTotalTicketCount returns zero for new cart`() {
        val cart = createCart()
        assertEquals(0, cart.getTotalTicketCount())
    }

    // ===========================================
    // VERSION FIELD TESTS (for optimistic locking)
    // ===========================================

    @Test
    fun `new cart has version zero`() {
        val cart = createCart()
        assertEquals(0L, cart.version)
    }

    // ===========================================
    // TOKEN UNIQUENESS TESTS
    // ===========================================

    @Test
    fun `each cart has unique token`() {
        val cart1 = createCart()
        val cart2 = createCart()

        assertNotEquals(cart1.token, cart2.token)
    }

    // ===========================================
    // SECURITY SCENARIO TESTS
    // ===========================================

    @Test
    fun `platform B cannot access platform A reservation`() {
        val platformA = UUID.randomUUID()
        val platformB = UUID.randomUUID()

        val cartOwnedByPlatformA = createCart(platformId = platformA)

        // Platform A can access its own cart
        assertDoesNotThrow {
            cartOwnedByPlatformA.validatePlatformOwnership(platformA)
        }

        // Platform B cannot access Platform A's cart
        assertThrows<VenuesException.AuthorizationFailure> {
            cartOwnedByPlatformA.validatePlatformOwnership(platformB)
        }
    }

    @Test
    fun `platform cannot access customer cart`() {
        val customerCart = createCart(platformId = null)
        val platformId = UUID.randomUUID()

        assertThrows<VenuesException.ValidationFailure> {
            customerCart.validatePlatformOwnership(platformId)
        }
    }

    // ===========================================
    // HELPER METHODS
    // ===========================================

    private fun createCart(
        platformId: UUID? = null,
        expiresAt: Instant = Instant.now().plusSeconds(420) // 7 minutes default
    ): Cart {
        return Cart(
            sessionId = sessionId,
            expiresAt = expiresAt,
            token = UUID.randomUUID(),
            userId = null,
            platformId = platformId,
            promoCode = null,
            discountAmount = null
        )
    }
}
