package app.venues.booking.service

import app.venues.shared.idempotency.IdempotencyService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.util.*

/**
 * Booking-specific idempotency service that uses cart token for key namespacing.
 *
 * Key Format: "booking:{endpoint}:{cartToken}:{idempotencyKey}"
 * - Ensures idempotency is scoped to a specific cart
 * - Prevents duplicate operations even if same idempotency key is used across different carts
 */
@Service
class BookingIdempotencyService(
    private val idempotencyService: IdempotencyService,
    private val objectMapper: ObjectMapper
) {
    companion object {
        private const val KEY_PREFIX = "booking"
    }

    /**
     * Execute supplier with idempotency protection scoped to a cart.
     *
     * @param idempotencyKey Optional idempotency key from client
     * @param cartToken Cart token for namespacing (null for operations that don't have a cart yet)
     * @param endpoint API endpoint identifier (e.g., "cart:add-seat")
     * @param responseType Expected response type for deserialization
     * @param supplier Function to execute if not cached
     * @return Result of supplier (either freshly computed or from cache)
     */
    fun <T : Any> withIdempotency(
        idempotencyKey: String?,
        cartToken: UUID?,
        endpoint: String,
        responseType: Class<T>,
        supplier: () -> T
    ): T {
        // Build namespace: include cart token if available
        val namespace = if (cartToken != null) {
            "$endpoint:$cartToken"
        } else {
            endpoint
        }

        return idempotencyService.withIdempotency(
            idempotencyKey = idempotencyKey,
            keyPrefix = KEY_PREFIX,
            namespace = namespace,
            responseType = responseType,
            supplier = supplier
        )
    }
}
