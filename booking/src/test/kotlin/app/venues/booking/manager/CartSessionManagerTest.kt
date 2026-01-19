package app.venues.booking.manager

import app.venues.booking.domain.Cart
import app.venues.booking.repository.CartRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*

/**
 * Tests for CartSessionManager expiration behavior.
 * Verifies customer vs staff cart expiration times with isStaffCart flag.
 *
 * Customer carts: 7 min initial, +5 min extensions, max 20 min total
 * Staff carts: 20 min initial, +10 min extensions, max 30 min total
 */
class CartSessionManagerTest {

    private val cartRepository: CartRepository = mockk(relaxed = true)
    private val entityManager = mockk<jakarta.persistence.EntityManager>(relaxed = true)
    private val cartSessionManager = CartSessionManager(cartRepository, entityManager)

    @Test
    fun `findOrCreateCart creates customer cart with 7 minute expiration by default`() {
        // Arrange
        val sessionId = UUID.randomUUID()
        val cartSlot = slot<Cart>()

        every { cartRepository.findByToken(any()) } returns null
        every { cartRepository.save(capture(cartSlot)) } answers { cartSlot.captured }

        val beforeCreation = Instant.now()

        // Act
        cartSessionManager.findOrCreateCart(
            token = null,
            sessionId = sessionId,
            isStaffCart = false // Explicit customer cart
        )

        // Assert
        verify { cartRepository.save(any()) }

        val savedCart = cartSlot.captured
        assertNotNull(savedCart)
        assertEquals(sessionId, savedCart.sessionId)

        // Verify expiration is approximately 7 minutes from now
        val expirationDuration = savedCart.expiresAt.epochSecond - beforeCreation.epochSecond
        assertTrue(expirationDuration >= 6 * 60, "Expiration should be at least 6 minutes")
        assertTrue(expirationDuration <= 8 * 60, "Expiration should be at most 8 minutes")
    }

    @Test
    fun `findOrCreateCart creates staff cart with 20 minute expiration when isStaffCart is true`() {
        // Arrange
        val sessionId = UUID.randomUUID()
        val cartSlot = slot<Cart>()

        every { cartRepository.findByToken(any()) } returns null
        every { cartRepository.save(capture(cartSlot)) } answers { cartSlot.captured }

        val beforeCreation = Instant.now()

        // Act
        cartSessionManager.findOrCreateCart(
            token = null,
            sessionId = sessionId,
            isStaffCart = true // Staff cart
        )

        // Assert
        verify { cartRepository.save(any()) }

        val savedCart = cartSlot.captured
        assertNotNull(savedCart)
        assertEquals(sessionId, savedCart.sessionId)

        // Verify expiration is approximately 20 minutes from now
        val expirationDuration = savedCart.expiresAt.epochSecond - beforeCreation.epochSecond
        assertTrue(expirationDuration >= 19 * 60, "Expiration should be at least 19 minutes")
        assertTrue(expirationDuration <= 21 * 60, "Expiration should be at most 21 minutes")
    }

    @Test
    fun `extending customer cart adds 5 minutes from current time`() {
        // Arrange
        val sessionId = UUID.randomUUID()
        val existingCart = Cart(
            sessionId = sessionId,
            expiresAt = Instant.now().plusSeconds(60), // expires in 1 min
            token = UUID.randomUUID()
        )

        every { cartRepository.findByToken(existingCart.token) } returns existingCart
        every { cartRepository.extendExpiration(any(), any(), any(), any()) } returns 1

        val beforeExtension = Instant.now()

        // Act - extend with customer parameters (default)
        val result = cartSessionManager.findOrCreateCart(
            token = existingCart.token,
            sessionId = sessionId,
            isStaffCart = false
        )

        // Assert
        verify { cartRepository.extendExpiration(existingCart.token, existingCart.version, any(), any()) }

        // Should extend by CART_EXTENSION_MINUTES (5 min) from NOW, not from expiration
        val expirationDuration = result.expiresAt.epochSecond - beforeExtension.epochSecond
        assertTrue(expirationDuration >= 4 * 60, "Should extend by at least 4 minutes from now")
        assertTrue(expirationDuration <= 6 * 60, "Should extend by at most 6 minutes from now")
    }

    @Test
    fun `extending staff cart adds 10 minutes from current time`() {
        // Arrange
        val sessionId = UUID.randomUUID()
        val existingCart = Cart(
            sessionId = sessionId,
            expiresAt = Instant.now().plusSeconds(60), // expires in 1 min
            token = UUID.randomUUID()
        )

        every { cartRepository.findByToken(existingCart.token) } returns existingCart
        every { cartRepository.extendExpiration(any(), any(), any(), any()) } returns 1

        val beforeExtension = Instant.now()

        // Act - extend with staff parameters
        val result = cartSessionManager.findOrCreateCart(
            token = existingCart.token,
            sessionId = sessionId,
            isStaffCart = true
        )

        // Assert
        verify { cartRepository.extendExpiration(existingCart.token, existingCart.version, any(), any()) }

        // Should extend by STAFF_CART_EXTENSION_MINUTES (10 min) from NOW
        val expirationDuration = result.expiresAt.epochSecond - beforeExtension.epochSecond
        assertTrue(expirationDuration >= 9 * 60, "Should extend by at least 9 minutes from now")
        assertTrue(expirationDuration <= 11 * 60, "Should extend by at most 11 minutes from now")
    }

    @Test
    fun `customer cart expiration respects max TTL of 20 minutes from creation`() {
        // Arrange
        val sessionId = UUID.randomUUID()
        val cart = Cart(
            sessionId = sessionId,
            expiresAt = Instant.now().plusSeconds(60), // expires in 1 min
            token = UUID.randomUUID()
        )

        // Simulate cart created 16 minutes ago
        val createdAt = Instant.now().minusSeconds(16 * 60)
        cart.createdAt = createdAt

        // Act - try to extend by 5 more minutes (would be 21 min total from creation)
        cart.extendExpiration(
            CartSessionManager.CART_EXTENSION_MINUTES.toLong(),
            CartSessionManager.CART_EXPIRATION_MINUTES.toLong(),
            CartSessionManager.CUSTOMER_MAX_CART_TTL_MINUTES.toLong()
        )

        // Assert - should cap at 20 minutes from creation
        val maxAllowedExpiration = createdAt.plusSeconds(20 * 60)
        assertTrue(
            cart.expiresAt.epochSecond <= maxAllowedExpiration.epochSecond + 1,
            "Customer cart expiration should not exceed 20 minutes from creation. " +
                    "Expected max: ${maxAllowedExpiration.epochSecond}, Got: ${cart.expiresAt.epochSecond}"
        )
    }

    @Test
    fun `staff cart expiration respects max TTL of 30 minutes from creation`() {
        // Arrange
        val sessionId = UUID.randomUUID()
        val cart = Cart(
            sessionId = sessionId,
            expiresAt = Instant.now().plusSeconds(60), // expires in 1 min
            token = UUID.randomUUID()
        )

        // Simulate cart created 25 minutes ago
        val createdAt = Instant.now().minusSeconds(25 * 60)
        cart.createdAt = createdAt

        // Act - try to extend by 10 more minutes (would be 35 min total from creation)
        cart.extendExpiration(
            CartSessionManager.STAFF_CART_EXTENSION_MINUTES.toLong(),
            CartSessionManager.STAFF_CART_EXPIRATION_MINUTES.toLong(),
            CartSessionManager.STAFF_MAX_CART_TTL_MINUTES.toLong()
        )

        // Assert - should cap at 30 minutes from creation
        val maxAllowedExpiration = createdAt.plusSeconds(30 * 60)
        assertTrue(
            cart.expiresAt.epochSecond <= maxAllowedExpiration.epochSecond + 1,
            "Staff cart expiration should not exceed 30 minutes from creation. " +
                    "Expected max: ${maxAllowedExpiration.epochSecond}, Got: ${cart.expiresAt.epochSecond}"
        )
    }

    @Test
    fun `CRITICAL - staff cart with existing token gets staff extension even without isStaffCart flag`() {
        // This test verifies the PROBLEM we just fixed!
        // When staff adds to existing cart, it should get 10 min extension, not 5 min

        // Arrange
        val sessionId = UUID.randomUUID()
        val existingCart = Cart(
            sessionId = sessionId,
            expiresAt = Instant.now().plusSeconds(60), // expires in 1 min
            token = UUID.randomUUID()
        )

        every { cartRepository.findByToken(existingCart.token) } returns existingCart
        every { cartRepository.extendExpiration(any(), any(), any(), any()) } returns 1

        val beforeExtension = Instant.now()

        // Act - Staff calls with isStaffCart=true
        val result = cartSessionManager.findOrCreateCart(
            token = existingCart.token,
            sessionId = sessionId,
            isStaffCart = true // CRITICAL: This ensures staff extension
        )

        // Assert
        verify { cartRepository.extendExpiration(existingCart.token, existingCart.version, any(), any()) }

        val expirationDuration = result.expiresAt.epochSecond - beforeExtension.epochSecond

        // Verify it got STAFF extension (10 min), not customer (5 min)
        assertTrue(
            expirationDuration >= 9 * 60,
            "Staff cart should get 10 min extension, but got only ${expirationDuration / 60} minutes"
        )
        assertTrue(expirationDuration <= 11 * 60, "Extension should be at most 11 minutes")
    }
}
