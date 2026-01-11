package app.venues.shared.idempotency

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class IdempotencyContextTest {

    private data class DummyResponse(val value: String)

    @Test
    fun `builds keys with scope`() {
        val context = IdempotencyContext(
            keyPrefix = "booking",
            operation = "cart:add-seat",
            scopeId = "cart-123",
            idempotencyKey = "idem-1",
            responseType = DummyResponse::class.java
        )

        assertEquals("booking:cart:add-seat:cart-123:idem-1", context.getCacheKey())
        assertEquals("lock:booking:cart:add-seat:cart-123:idem-1", context.getLockKey())
        assertEquals(
            "prefix=booking operation=cart:add-seat scope=cart-123 key=idem-1",
            context.getDescription()
        )
    }

    @Test
    fun `builds keys without scope`() {
        val context = IdempotencyContext(
            keyPrefix = "platform",
            operation = "hold",
            scopeId = null,
            idempotencyKey = "idem-2",
            responseType = DummyResponse::class.java
        )

        assertEquals("platform:hold:idem-2", context.getCacheKey())
        assertEquals("lock:platform:hold:idem-2", context.getLockKey())
        assertEquals("prefix=platform operation=hold key=idem-2", context.getDescription())
    }

    @Test
    fun `rejects blank inputs`() {
        assertThrows(IllegalArgumentException::class.java) {
            IdempotencyContext(
                keyPrefix = "",
                operation = "op",
                scopeId = null,
                idempotencyKey = "key",
                responseType = DummyResponse::class.java
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            IdempotencyContext(
                keyPrefix = "prefix",
                operation = "",
                scopeId = null,
                idempotencyKey = "key",
                responseType = DummyResponse::class.java
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            IdempotencyContext(
                keyPrefix = "prefix",
                operation = "op",
                scopeId = null,
                idempotencyKey = "",
                responseType = DummyResponse::class.java
            )
        }
    }
}
