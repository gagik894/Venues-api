package app.venues.booking.service

import app.venues.booking.api.CartQueryApi
import app.venues.booking.api.dto.CartSummaryResponse
import app.venues.booking.api.mapper.CartMapper
import app.venues.booking.manager.CartSessionManager
import app.venues.common.exception.VenuesException
import app.venues.event.api.EventApi
import app.venues.seating.api.SeatingApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.*

/**
 * Service for **Query** operations related to the cart.
 *
 * This service is responsible for all read-only, complex aggregations
 * of cart data, primarily for building the cart summary.
 *
 * All mutation (write) operations are handled by `CartService`.
 */
@Service
@Transactional(readOnly = true)
class CartQueryService(
    private val cartSessionManager: CartSessionManager,
    private val eventApi: EventApi,
    private val cartMapper: CartMapper,
    private val seatingApi: SeatingApi
) : CartQueryApi {
    private val logger = KotlinLogging.logger {}

    /**
     * Retrieves cart summary with optimized loading strategy.
     *
     * Uses @EntityGraph to load cart + all items in a single query,
     * then performs batch API calls for external data (seating info, templates).
     * Returns snapshotted prices, not current live prices.
     *
     * Performance: O(1) database queries instead of O(n+1).
     */
    override fun getCartSummary(token: UUID): CartSummaryResponse {
        // Load cart with ALL items in single query (via @EntityGraph)
        val cart = cartSessionManager.getActiveCartWithItems(token)

        val sessionDto = eventApi.getEventSessionInfo(cart.sessionId)
            ?: throw VenuesException.ResourceNotFound("Session not found")

        // Collections are already loaded via @EntityGraph - no additional queries
        val seats = cart.seats
        val gaItems = cart.gaItems
        val tables = cart.tables

        // Return empty cart summary if no items (valid state after removing last item)
        if (seats.isEmpty() && gaItems.isEmpty() && tables.isEmpty()) {
            return cartMapper.toCartSummary(
                token = token,
                seats = emptyList(),
                gaItems = emptyList(),
                tables = emptyList(),
                totalPrice = BigDecimal.ZERO,
                currency = sessionDto.currency,
                expiresAt = cart.expiresAt.toString(),
                sessionId = cart.sessionId,
                eventTitle = sessionDto.eventTitle
            )
        }

        // 2. Collect all unique IDs needed for batch fetching
        val seatIds = seats.map { it.seatId }.distinct()
        val gaLevelIds = gaItems.map { it.gaAreaId }.distinct()
        val tableIds = tables.map { it.tableId }.distinct()

        // 3. Make batch calls to repositories and APIs
        val seatTemplateNames = eventApi.getSeatPriceTemplateNames(cart.sessionId, seatIds)
        val gaTemplateNames = eventApi.getGaPriceTemplateNames(cart.sessionId, gaLevelIds)

        // Batch fetch seat info from seating API
        val seatInfoMap = seatingApi.getSeatInfoBatch(seatIds).associateBy { it.id }

        // Batch fetch GA info - we need to fetch each GA area individually since no batch method exists
        val gaInfoMap = gaLevelIds.mapNotNull { gaId ->
            seatingApi.getGaInfo(gaId)?.let { gaId to it }
        }.toMap()

        // Batch fetch table info
        val tableInfoMap = tableIds.mapNotNull { tableId ->
            seatingApi.getTableInfo(tableId)?.let { tableId to it }
        }.toMap()

        // 4. Map results in memory (no more calls inside loops)
        val seatResponses = seats.mapNotNull { cartSeat ->
            val templateName = seatTemplateNames[cartSeat.seatId]
            val seatInfo = seatInfoMap[cartSeat.seatId]

            if (seatInfo == null) {
                logger.warn { "Missing data for cart seat: ${cartSeat.seatId}" }
                null // Skip item if data is inconsistent
            } else {
                cartMapper.toCartSeatResponse(
                    code = seatInfo.code,
                    seatNumber = seatInfo.seatNumber,
                    rowLabel = seatInfo.rowLabel,
                    levelName = seatInfo.zoneName,
                    price = cartSeat.unitPrice,
                    priceTemplateName = templateName
                )
            }
        }

        val gaItemResponses = gaItems.mapNotNull { cartItem ->
            val templateName = gaTemplateNames[cartItem.gaAreaId]
            val gaInfo = gaInfoMap[cartItem.gaAreaId]

            if (gaInfo == null) {
                logger.warn { "Missing data for cart GA item: ${cartItem.gaAreaId}" }
                null
            } else {
                cartMapper.toCartGAItemResponse(
                    quantity = cartItem.quantity,
                    code = gaInfo.code,
                    levelName = gaInfo.name,
                    unitPrice = cartItem.unitPrice,
                    priceTemplateName = templateName
                )
            }
        }

        val tableResponses = tables.mapNotNull { cartTable ->
            val tableInfo = tableInfoMap[cartTable.tableId]
            if (tableInfo == null) {
                logger.warn { "Missing data for cart table item: ${cartTable.tableId}" }
                null
            } else {
                cartMapper.toCartTableResponse(
                    code = tableInfo.code,
                    tableName = tableInfo.tableNumber,
                    price = cartTable.unitPrice
                )
            }
        }

        val total = seatResponses.sumOf { BigDecimal(it.price) }
            .add(gaItemResponses.sumOf { BigDecimal(it.totalPrice) })
            .add(tableResponses.sumOf { it.price })

        return cartMapper.toCartSummary(
            token = token,
            seats = seatResponses,
            gaItems = gaItemResponses,
            tables = tableResponses,
            totalPrice = total,
            currency = sessionDto.currency,
            expiresAt = cart.expiresAt.toString(),
            sessionId = cart.sessionId,
            eventTitle = sessionDto.eventTitle
        )
    }
}

