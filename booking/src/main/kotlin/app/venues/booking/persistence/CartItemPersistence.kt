package app.venues.booking.persistence

import app.venues.booking.domain.Cart
import app.venues.booking.domain.CartItem
import app.venues.booking.domain.CartSeat
import app.venues.booking.repository.CartItemRepository
import app.venues.booking.repository.CartSeatRepository
import app.venues.booking.service.InventoryChangePublisher
import app.venues.common.exception.VenuesException
import app.venues.event.api.EventApi
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
    private val eventApi: EventApi,
    private val seatingApi: SeatingApi,
    private val eventPublisher: ApplicationEventPublisher,
    private val inventoryChangePublisher: InventoryChangePublisher
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

        // Add to cart's collection for proper bidirectional relationship management
        cart.seats.add(cartSeat)

        val saved = cartSeatRepository.save(cartSeat)

        logger.info { "Seat added to cart: $seatIdentifier, price=$price, cart=${cart.token}" }

        return saved
    }

    fun saveOrUpdateGAItem(
        cart: Cart,
        sessionId: UUID,
        gaAreaId: Long,
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
                gaAreaId = gaAreaId,
                unitPrice = unitPrice,
                quantity = quantityToAdd
            )
            // Add to cart's collection for proper bidirectional relationship management
            cart.gaItems.add(newItem)
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

        publishGAAvailabilityEvent(sessionId, gaAreaId, levelIdentifier, levelName)

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

        publishGAAvailabilityEvent(item.sessionId, item.gaAreaId, levelIdentifier, levelName)
        return savedItem
    }

    fun removeGAItem(
        item: CartItem,
        levelIdentifier: String,
        levelName: String
    ) {
        val sessionId = item.sessionId
        val gaAreaId = item.gaAreaId
        val cart = item.cart

        // Remove from cart's collection for proper bidirectional relationship management
        cart.gaItems.remove(item)
        cartItemRepository.delete(item)

        logger.info { "GA item removed: $levelIdentifier, cart=${cart.token}" }

        publishGAAvailabilityEvent(sessionId, gaAreaId, levelIdentifier, levelName)
    }

    fun checkSeatAlreadyInCart(cart: Cart, seatId: Long): Boolean {
        val existingSeats = cartSeatRepository.findByCart(cart)
        return existingSeats.any { it.seatId == seatId }
    }

    fun findExistingGAItem(cart: Cart, gaAreaId: Long): CartItem? {
        return cartItemRepository.findByCartAndGaAreaId(cart, gaAreaId)
    }

    fun removeSeat(
        cart: Cart,
        seatId: Long,
        sessionId: UUID,
        seatIdentifier: String,
    ) {
        val cartSeat = cart.seats.find { it.seatId == seatId }
            ?: throw VenuesException.ResourceNotFound("Seat not found in cart")

        // Remove from cart's collection for proper bidirectional relationship management
        cart.seats.remove(cartSeat)
        cartSeatRepository.delete(cartSeat)

        logger.info { "Seat removed from cart: $seatIdentifier, cart=${cart.token}" }
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
        val gaAvailability = eventApi.getGaAvailability(sessionId, gaAreaId)
        val capacity = gaAvailability?.capacity ?: 0
        val availableTickets = capacity - (gaAvailability?.soldCount ?: 0)

        inventoryChangePublisher.gaAvailabilityChanged(
            sessionId = sessionId,
            gaAreaId = gaAreaId,
            levelIdentifier = levelIdentifier,
            availableTickets = availableTickets,
            totalCapacity = capacity
        )
    }
}

