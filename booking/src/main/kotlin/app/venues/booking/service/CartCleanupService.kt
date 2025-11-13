package app.venues.booking.service

import app.venues.booking.repository.CartRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Service for cart cleanup operations.
 *
 * This is a standalone service responsible for finding and deleting
 * expired carts and releasing their inventory. It is designed to be
 * called by a scheduled job (`CartCleanupJob`).
 *
 * It delegates the actual deletion to `CartCleanupHelper` to ensure
 * correct transactional propagation.
 */
@Service
class CartCleanupService(
    private val cartRepository: CartRepository,
    private val cartCleanupHelper: CartCleanupHelper // Inject the helper
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Deletes expired cart sessions and releases all inventory.
     * Called by scheduled cleanup job.
     */
    fun deleteExpiredCarts(): Int {
        val now = Instant.now()
        val expiredCarts = cartRepository.findByExpiresAtBefore(now)

        if (expiredCarts.isEmpty()) {
            return 0
        }

        var totalItemsReleased = 0
        var successfulDeletions = 0

        // Process each cart in its own transaction via the helper component
        // This is now an inter-class call, so the proxy will work.
        expiredCarts.forEach { cart ->
            val result = cartCleanupHelper.deleteSingleCart(cart)
            if (result != null) {
                totalItemsReleased += result.itemsReleased
                successfulDeletions++
            }
        }

        if (totalItemsReleased > 0) {
            logger.info { "Cleanup complete: Deleted $successfulDeletions carts, released $totalItemsReleased items" }
        }

        return successfulDeletions
    }
}