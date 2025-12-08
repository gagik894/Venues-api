package app.venues.booking.manager

import app.venues.booking.domain.Cart
import app.venues.booking.repository.CartRepository
import io.mockk.MockKAnnotations
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.*

/**
 * Tests for CartSessionManager custom TTL handling and caps (gov-grade spike readiness).
 */
class CartSessionManagerTtlTest {

    private lateinit var cartRepository: CartRepository
    private lateinit var manager: CartSessionManager

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        cartRepository = io.mockk.mockk(relaxed = true)
        clearMocks(cartRepository)
        manager = CartSessionManager(cartRepository)
    }

    @Test
    fun `custom ttl is capped at 30 minutes`() {
        val sessionId = UUID.randomUUID()
        val savedCart = slot<Cart>()

        every { cartRepository.findByToken(any()) } returns null
        every { cartRepository.save(capture(savedCart)) } answers { savedCart.captured }

        val start = Instant.now()
        manager.findOrCreateCart(
            token = null,
            sessionId = sessionId,
            userId = null,
            isStaffCart = false,
            platformId = UUID.randomUUID(),
            customTtlSeconds = 4000L // > 30 minutes
        )

        val expiresAt = savedCart.captured.expiresAt
        val maxExpected = start.plusSeconds(30 * 60L + 2) // small buffer
        assertTrue(expiresAt.isBefore(maxExpected) || expiresAt == maxExpected)
    }

    @Test
    fun `custom ttl shorter than cap is honored`() {
        val sessionId = UUID.randomUUID()
        val savedCart = slot<Cart>()

        every { cartRepository.findByToken(any()) } returns null
        every { cartRepository.save(capture(savedCart)) } answers { savedCart.captured }

        val start = Instant.now()
        manager.findOrCreateCart(
            token = null,
            sessionId = sessionId,
            userId = null,
            isStaffCart = false,
            platformId = UUID.randomUUID(),
            customTtlSeconds = 120L // 2 minutes
        )

        val expiresAt = savedCart.captured.expiresAt
        val delta = Duration.between(start, expiresAt).seconds
        assertTrue(delta in 110..130) // allow small timing variance
    }
}

