package app.venues.booking.persistence

import app.venues.booking.domain.Cart
import app.venues.booking.domain.CartItem
import app.venues.booking.domain.CartSeat
import app.venues.booking.event.GAAvailabilityChangedEvent
import app.venues.booking.event.SeatReleasedEvent
import app.venues.booking.event.SeatReservedEvent
import app.venues.booking.repository.CartItemRepository
import app.venues.booking.repository.CartSeatRepository
import app.venues.common.exception.VenuesException
import app.venues.event.repository.SessionGAConfigRepository
import app.venues.seating.api.SeatingApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.*

/**
 * Handles cart item persistence and domain event publishing.
 * Manages relationships between cart and cart items (seats/GA).
 */
@Component
class CartItemPersistence(
    private val cartSeatRepository: CartSeatRepository,
    private val cartItemRepository: CartItemRepository,
    private val sessionGAConfigRepository: SessionGAConfigRepository,
    private val seatingApi: SeatingApi,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val logger = KotlinLogging.logger {}

    fun saveSeatToCart(
        cart: Cart,
        sessionId: UUID,
        seatId: Long,
        seatIdentifier: String,
        price: BigDecimal
    ): CartSeat {
        val cartSeat = CartSeat(
            cart = cart,
            sessionId = sessionId,
            seatId = seatId,
            unitPrice = price
        )

        val saved = cartSeatRepository.save(cartSeat)

        logger.info { "Seat added to cart: $seatIdentifier, price=$price, cart=${cart.token}" }

        eventPublisher.publishEvent(
            SeatReservedEvent(
                sessionId = sessionId,
                seatIdentifier = seatIdentifier,
//                reservationToken = cart.token,
//                expiresAt = cart.expiresAt.toString()
            )
        )

        return saved
    }

    fun saveOrUpdateGAItem(
        cart: Cart,
        sessionId: UUID,
        levelId: Long,
        levelIdentifier: String,
        levelName: String,
        quantityToAdd: Int,
        unitPrice: BigDecimal,
        existingItem: CartItem? = null
    ): Pair<CartItem, Boolean> {
        val (savedItem, isUpdate) = if (existingItem != null) {
            existingItem.quantity += quantityToAdd
            cartItemRepository.save(existingItem) to true
        } else {
            val newItem = CartItem(
                cart = cart,
                sessionId = sessionId,
                levelId = levelId,
                unitPrice = unitPrice,
                quantity = quantityToAdd
            )
            cartItemRepository.save(newItem) to false
        }

        if (isUpdate) {
            logger.info {
                "GA quantity updated: $levelIdentifier, total=${savedItem.quantity} " +
                        "(+$quantityToAdd), price=$unitPrice, cart=${cart.token}"
            }
        } else {
            logger.info {
                "GA added to cart: $levelIdentifier, qty=${savedItem.quantity}, " +
                        "price=$unitPrice, cart=${cart.token}"
            }
        }

        publishGAAvailabilityEvent(sessionId, levelId, levelIdentifier, levelName)

        return savedItem to isUpdate
    }

    fun updateGAItemQuantity(
        item: CartItem,
        newQuantity: Int,
        levelIdentifier: String,
        levelName: String
    ): CartItem {
        item.quantity = newQuantity
        val savedItem = cartItemRepository.save(item)

        logger.info { "GA quantity set: $levelIdentifier, total=${savedItem.quantity}, cart=${item.cart.token}" }

        publishGAAvailabilityEvent(item.sessionId, item.levelId, levelIdentifier, levelName)
        return savedItem
    }

    fun removeGAItem(
        item: CartItem,
        levelIdentifier: String,
        levelName: String
    ) {
        val sessionId = item.sessionId
        val levelId = item.levelId

        cartItemRepository.delete(item)

        logger.info { "GA item removed: $levelIdentifier, cart=${item.cart.token}" }

        publishGAAvailabilityEvent(sessionId, levelId, levelIdentifier, levelName)
    }

    fun checkSeatAlreadyInCart(cart: Cart, seatId: Long): Boolean {
        val existingSeats = cartSeatRepository.findByCart(cart)
        return existingSeats.any { it.seatId == seatId }
    }

    fun findExistingGAItem(cart: Cart, levelId: Long): CartItem? {
        return cartItemRepository.findByCartAndLevelId(cart, levelId)
    }

    fun removeSeat(
        cart: Cart,
        seatId: Long,
        sessionId: UUID,
        seatIdentifier: String,
        levelName: String
    ) {
        val cartSeats = cartSeatRepository.findByCart(cart)
        val cartSeat = cartSeats.find { it.seatId == seatId }
            ?: throw VenuesException.ResourceNotFound("Seat not found in cart")

        cartSeatRepository.delete(cartSeat)

        logger.info { "Seat removed from cart: $seatIdentifier, cart=${cart.token}" }

        eventPublisher.publishEvent(
            SeatReleasedEvent(
                sessionId = sessionId,
                seatIdentifier = seatIdentifier
            )
        )
    }

    fun getAllSeats(cart: Cart): List<CartSeat> = cartSeatRepository.findByCart(cart)

    fun getAllGAItems(cart: Cart): List<CartItem> = cartItemRepository.findByCart(cart)

    fun deleteAllItems(cart: Cart) {
        val seats = cartSeatRepository.findByCart(cart)
        val items = cartItemRepository.findByCart(cart)

        cartSeatRepository.deleteAll(seats)
        cartItemRepository.deleteAll(items)

        // Ensure deletion is flushed before inventory release
        cartSeatRepository.flush()
        cartItemRepository.flush()
    }

    private fun publishGAAvailabilityEvent(
        sessionId: UUID,
        gaAreaId: Long,
        levelIdentifier: String,
        levelName: String
    ) {
        val gaConfig = sessionGAConfigRepository.findBySessionIdAndGaAreaId(sessionId, gaAreaId)
        val capacity = gaConfig?.capacity ?: 0
        val availableTickets = capacity - (gaConfig?.soldCount ?: 0)

        eventPublisher.publishEvent(
            GAAvailabilityChangedEvent(
                sessionId = sessionId,
                levelIdentifier = levelIdentifier,
                levelName = levelName,
                availableTickets = availableTickets,
                totalCapacity = capacity
            )
        )
    }
}

