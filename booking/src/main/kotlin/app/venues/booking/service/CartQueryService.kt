package app.venues.booking.service

import app.venues.booking.api.CartQueryApi
import app.venues.booking.api.dto.CartSummaryResponse
import app.venues.booking.api.mapper.CartMapper
import app.venues.booking.manager.CartSessionManager
import app.venues.booking.persistence.CartItemPersistence
import app.venues.booking.persistence.CartTablePersistence
import app.venues.common.exception.VenuesException
import app.venues.event.repository.EventSessionRepository
import app.venues.event.repository.SessionLevelConfigRepository
import app.venues.event.repository.SessionSeatConfigRepository
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
    private val cartItemPersistence: CartItemPersistence,
    private val cartTablePersistence: CartTablePersistence,
    private val eventSessionRepository: EventSessionRepository,
    private val sessionSeatConfigRepository: SessionSeatConfigRepository,
    private val sessionLevelConfigRepository: SessionLevelConfigRepository,
    private val cartMapper: CartMapper,
    private val seatingApi: SeatingApi
) : CartQueryApi {
    private val logger = KotlinLogging.logger {}

    /**
     * Retrieves cart summary. Touches cart session to track activity.
     * Returns snapshotted prices, not current live prices.
     *
     * This operation is optimized to use batch fetching to prevent N+1 queries.
     */
    override fun getCartSummary(token: UUID): CartSummaryResponse {
        val cart = cartSessionManager.getActiveCart(token)
        cartSessionManager.touchCart(cart) // 'touch' is a write, but on the session, so OK

        val session = eventSessionRepository.findById(cart.sessionId)
            .orElseThrow { VenuesException.ResourceNotFound("Session not found") }

        // 1. Get all cart items from local DB
        val seats = cartItemPersistence.getAllSeats(cart)
        val gaItems = cartItemPersistence.getAllGAItems(cart)
        val tables = cartTablePersistence.getAllTables(cart)

        if (seats.isEmpty() && gaItems.isEmpty() && tables.isEmpty()) {
            throw VenuesException.ResourceNotFound("Cart is empty")
        }

        // 2. Collect all unique IDs needed for batch fetching
        val seatIds = seats.map { it.seatId }.distinct()
        val gaLevelIds = gaItems.map { it.levelId }.distinct()
        val tableIds = tables.map { it.tableId }.distinct()

        // 3. Make batch calls to repositories and APIs
        val seatConfigs = sessionSeatConfigRepository.findBySessionIdAndSeatIdIn(cart.sessionId, seatIds)
            .associateBy { it.seatId }
        val levelConfigs = sessionLevelConfigRepository.findBySessionIdAndLevelIdIn(cart.sessionId, gaLevelIds)
            .associateBy { it.levelId }

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
            val config = seatConfigs[cartSeat.seatId]
            val seatInfo = seatInfoMap[cartSeat.seatId]

            if (config == null || seatInfo == null) {
                logger.warn { "Missing data for cart seat: ${cartSeat.seatId}" }
                null // Skip item if data is inconsistent
            } else {
                cartMapper.toCartSeatResponse(
                    cartSeat = cartSeat,
                    seatIdentifier = seatInfo.code,
                    seatNumber = seatInfo.seatNumber,
                    rowLabel = seatInfo.rowLabel,
                    levelName = seatInfo.zoneName,
                    levelIdentifier = seatInfo.code.substringBefore("_"),
                    price = cartSeat.unitPrice,
                    priceTemplateName = config.priceTemplate?.templateName
                )
            }
        }

        val gaItemResponses = gaItems.mapNotNull { cartItem ->
            val config = levelConfigs[cartItem.levelId]
            val gaInfo = gaInfoMap[cartItem.levelId]

            if (config == null || gaInfo == null) {
                logger.warn { "Missing data for cart GA item: ${cartItem.levelId}" }
                null
            } else {
                cartMapper.toCartGAItemResponse(
                    cartItem = cartItem,
                    levelIdentifier = gaInfo.code,
                    levelName = gaInfo.name,
                    unitPrice = cartItem.unitPrice,
                    priceTemplateName = config.priceTemplate?.templateName
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
                    cartTable = cartTable,
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
            currency = session.event.currency,
            expiresAt = cart.expiresAt.toString(),
            sessionId = cart.sessionId,
            eventTitle = session.event.title
        )
    }
}