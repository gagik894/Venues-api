package app.venues.booking.service

import app.venues.booking.domain.Cart
import app.venues.booking.persistence.InventoryReservationHandler
import app.venues.booking.repository.CartItemRepository
import app.venues.booking.repository.CartRepository
import app.venues.booking.repository.CartSeatRepository
import app.venues.booking.repository.CartTableRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Helper component for cart cleanup operations.
 *
 * This class is separated from `CartCleanupService` to solve the
 * Spring AOP self-invocation problem. By placing the @Transactional
 * method in its own bean, we ensure that it is proxied correctly
 * when called from another service.
 */
@Component
class CartCleanupHelper(
    private val cartRepository: CartRepository,
    private val cartSeatRepository: CartSeatRepository,
    private val cartItemRepository: CartItemRepository,
    private val cartTableRepository: CartTableRepository,
    private val inventoryReservation: InventoryReservationHandler,
    private val tableReservationService: TableReservationService
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Deletes a single expired cart in its own independent transaction.
     * Uses REQUIRES_NEW to ensure isolation from the main cleanup loop.
     * If one cart fails to delete, it will not roll back the others.
     *
     * This method MUST be in a separate class from the service that calls it
     * for the @Transactional proxy to work.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun deleteSingleCart(cart: Cart): DeletionResult? {
        return try {
            val seats = cartSeatRepository.findByCart(cart)
            val gaItems = cartItemRepository.findByCart(cart)
            val tables = cartTableRepository.findByCart(cart)

            // Release seat inventory
            seats.forEach { cartSeat ->
                try {
                    inventoryReservation.releaseSeat(cart.sessionId, cartSeat.seatId)
                } catch (e: Exception) {
                    logger.warn { "Failed to release seat ${cartSeat.seatId} from cart ${cart.token}: ${e.message}" }
                }
            }

            // Release GA capacity
            gaItems.forEach { cartItem ->
                try {
                    inventoryReservation.releaseGATickets(cart.sessionId, cartItem.levelId, cartItem.quantity)
                } catch (e: Exception) {
                    logger.warn { "Failed to release GA for level ${cartItem.levelId} from cart ${cart.token}: ${e.message}" }
                }
            }

            // Release table inventory
            tables.forEach { cartTable ->
                try {
                    tableReservationService.releaseTable(cart.sessionId, cartTable.tableId)
                } catch (e: Exception) {
                    logger.warn { "Failed to release table ${cartTable.tableId} from cart ${cart.token}: ${e.message}" }
                }
            }

            val itemsReleased = seats.size + gaItems.size + tables.sumOf { it.seatCount }

            // Delete cart (CASCADE deletes items)
            cartRepository.delete(cart)

            logger.info { "Expired cart deleted: ${cart.token}, released ${seats.size} seats, ${gaItems.size} GA items, ${tables.size} tables" }

            DeletionResult(itemsReleased)
        } catch (e: Exception) {
            logger.error(e) { "Failed to process expired cart ${cart.token}: ${e.message}" }
            null
        }
    }

    data class DeletionResult(val itemsReleased: Int)
}