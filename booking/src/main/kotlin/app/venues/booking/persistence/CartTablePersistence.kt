package app.venues.booking.persistence

import app.venues.booking.domain.Cart
import app.venues.booking.domain.CartTable
import app.venues.booking.repository.CartTableRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.*

/**
 * Handles cart table persistence operations.
 * Manages CartTable records in the database.
 */
@Component
@Transactional
class CartTablePersistence(
    private val cartTableRepository: CartTableRepository
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Save table to cart.
     */
    fun saveTableToCart(
        cart: Cart,
        sessionId: UUID,
        tableId: Long,
        tableName: String,
        unitPrice: BigDecimal
    ): CartTable {
        val cartTable = CartTable(
            cart = cart,
            sessionId = sessionId,
            tableId = tableId,
            unitPrice = unitPrice
        )

        // Add to cart's collection for proper bidirectional relationship management
        cart.tables.add(cartTable)

        val saved = cartTableRepository.save(cartTable)
        logger.debug { "Saved table $tableName to cart ${cart.token}" }
        return saved
    }

    /**
     * Remove table from cart.
     */
    fun removeTableFromCart(cart: Cart, tableId: Long) {
        cartTableRepository.findByCartAndTableId(cart, tableId)?.let { cartTable ->
            cartTableRepository.delete(cartTable)
            logger.debug { "Removed table $tableId from cart ${cart.token}" }
        }
    }

    /**
     * Get all tables in cart.
     */
    fun getAllTables(cart: Cart): List<CartTable> {
        return cartTableRepository.findByCart(cart)
    }

    /**
     * Count tables in cart.
     */
    fun countTables(cart: Cart): Int {
        return cartTableRepository.countByCart(cart)
    }

    /**
     * Check if table is in cart.
     */
    fun isTableInCart(cart: Cart, tableId: Long): Boolean {
        return cartTableRepository.existsByCartAndTableId(cart, tableId)
    }

    /**
     * Remove all tables from cart.
     */
    fun clearAllTables(cart: Cart) {
        val tables = cartTableRepository.findByCart(cart)
        cartTableRepository.deleteAll(tables)
        logger.debug { "Cleared ${tables.size} tables from cart ${cart.token}" }
    }

    /**
     * Get cart table by session and table ID (for cleanup).
     */
    fun findBySessionAndTable(sessionId: UUID, tableId: Long): CartTable? {
        return cartTableRepository.findBySessionIdAndTableId(sessionId, tableId)
    }
}

