package app.venues.booking.repository

import app.venues.booking.domain.Cart
import app.venues.booking.domain.CartTable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/**
 * Repository for CartTable entity operations.
 */
@Repository
interface CartTableRepository : JpaRepository<CartTable, Long> {

    /**
     * Find all tables in a cart
     */
    fun findByCart(cart: Cart): List<CartTable>

    /**
     * Check if specific table is already in cart
     */
    fun existsByCartAndTableId(cart: Cart, tableId: Long): Boolean

    /**
     * Find table in cart by table ID
     */
    fun findByCartAndTableId(cart: Cart, tableId: Long): CartTable?

    /**
     * Check if cart has any tables from a specific session
     */
    fun existsByCartAndSessionId(cart: Cart, sessionId: Long): Boolean

    /**
     * Count tables in cart
     */
    fun countByCart(cart: Cart): Int

    /**
     * Find table by session and table ID
     */
    fun findBySessionIdAndTableId(sessionId: Long, tableId: Long): CartTable?

    /**
     * Check if a table is reserved by any cart
     */
    @Query(
        """
        SELECT COUNT(ct) > 0 FROM CartTable ct
        WHERE ct.sessionId = :sessionId
        AND ct.tableId = :tableId
    """
    )
    fun isTableReservedByAnyCart(sessionId: Long, tableId: Long): Boolean
}

