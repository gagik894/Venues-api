package app.venues.shared.idempotency

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * Tests for IdempotencyKeyExtractor utility.
 *
 * Ensures correct extraction of idempotency keys and scope identifiers
 * from controller method parameters using Spring annotations.
 */
class IdempotencyKeyExtractorTest {

    // ===========================================
    // IDEMPOTENCY KEY EXTRACTION TESTS
    // ===========================================

    @Test
    fun `extractIdempotencyKey finds key from RequestHeader`() {
        val method = TestController::class.java.getMethod(
            "withIdempotencyKey",
            String::class.java,
            String::class.java
        )
        val args = arrayOf<Any>("my-idempotency-key", "request-data")

        val result = IdempotencyKeyExtractor.extractIdempotencyKey(method, args)

        assertEquals("my-idempotency-key", result)
    }

    @Test
    fun `extractIdempotencyKey returns null when header not present`() {
        val method = TestController::class.java.getMethod(
            "withoutIdempotencyKey",
            String::class.java
        )
        val args = arrayOf<Any>("request-data")

        val result = IdempotencyKeyExtractor.extractIdempotencyKey(method, args)

        assertNull(result)
    }

    @Test
    fun `extractIdempotencyKey handles null header value`() {
        val method = TestController::class.java.getMethod(
            "withIdempotencyKey",
            String::class.java,
            String::class.java
        )
        val args = arrayOf<Any?>(null, "request-data")

        val result = IdempotencyKeyExtractor.extractIdempotencyKey(method, args as Array<Any>)

        assertNull(result)
    }

    // ===========================================
    // HEADER EXTRACTION TESTS
    // ===========================================

    @Test
    fun `extractHeaderValue finds custom header`() {
        val method = TestController::class.java.getMethod(
            "withCustomHeader",
            UUID::class.java,
            String::class.java
        )
        val platformId = UUID.randomUUID()
        val args = arrayOf<Any>(platformId, "request-data")

        val result = IdempotencyKeyExtractor.extractHeaderValue(method, args, "X-Platform-ID")

        assertEquals(platformId.toString(), result)
    }

    @Test
    fun `extractHeaderValue is case-insensitive`() {
        val method = TestController::class.java.getMethod(
            "withCustomHeader",
            UUID::class.java,
            String::class.java
        )
        val platformId = UUID.randomUUID()
        val args = arrayOf<Any>(platformId, "request-data")

        val result = IdempotencyKeyExtractor.extractHeaderValue(method, args, "x-platform-id")

        assertEquals(platformId.toString(), result)
    }

    // ===========================================
    // REQUEST PARAM EXTRACTION TESTS
    // ===========================================

    @Test
    fun `extractRequestParam finds param by name`() {
        val method = TestController::class.java.getMethod(
            "withRequestParam",
            UUID::class.java,
            String::class.java
        )
        val token = UUID.randomUUID()
        val args = arrayOf<Any>(token, "request-data")

        val result = IdempotencyKeyExtractor.extractRequestParam(method, args, "token")

        assertEquals(token.toString(), result)
    }

    @Test
    fun `extractRequestParam returns null when param not found`() {
        val method = TestController::class.java.getMethod(
            "withRequestParam",
            UUID::class.java,
            String::class.java
        )
        val args = arrayOf<Any>(UUID.randomUUID(), "request-data")

        val result = IdempotencyKeyExtractor.extractRequestParam(method, args, "nonexistent")

        assertNull(result)
    }

    @Test
    fun `extractRequestParam handles null param value`() {
        val method = TestController::class.java.getMethod(
            "withRequestParam",
            UUID::class.java,
            String::class.java
        )
        val args = arrayOf<Any?>(null, "request-data")

        val result = IdempotencyKeyExtractor.extractRequestParam(method, args as Array<Any>, "token")

        assertNull(result)
    }

    // ===========================================
    // PATH VARIABLE EXTRACTION TESTS
    // ===========================================

    @Test
    fun `extractPathVariable finds variable by name`() {
        val method = TestController::class.java.getMethod(
            "withPathVariable",
            UUID::class.java,
            String::class.java
        )
        val bookingId = UUID.randomUUID()
        val args = arrayOf<Any>(bookingId, "request-data")

        val result = IdempotencyKeyExtractor.extractPathVariable(method, args, "id")

        assertEquals(bookingId.toString(), result)
    }

    @Test
    fun `extractPathVariable returns null when variable not found`() {
        val method = TestController::class.java.getMethod(
            "withPathVariable",
            UUID::class.java,
            String::class.java
        )
        val args = arrayOf<Any>(UUID.randomUUID(), "request-data")

        val result = IdempotencyKeyExtractor.extractPathVariable(method, args, "nonexistent")

        assertNull(result)
    }

    // ===========================================
    // COOKIE EXTRACTION TESTS
    // ===========================================

    @Test
    fun `extractCookieValue finds cookie by name`() {
        val method = TestController::class.java.getMethod(
            "withCookie",
            UUID::class.java,
            String::class.java
        )
        val cartToken = UUID.randomUUID()
        val args = arrayOf<Any>(cartToken, "request-data")

        val result = IdempotencyKeyExtractor.extractCookieValue(method, args, "cart_token")

        assertEquals(cartToken.toString(), result)
    }

    @Test
    fun `extractCookieValue returns null when cookie not found`() {
        val method = TestController::class.java.getMethod(
            "withCookie",
            UUID::class.java,
            String::class.java
        )
        val args = arrayOf<Any>(UUID.randomUUID(), "request-data")

        val result = IdempotencyKeyExtractor.extractCookieValue(method, args, "nonexistent")

        assertNull(result)
    }

    // ===========================================
    // SCOPE ID EXTRACTION TESTS
    // ===========================================

    @Test
    fun `extractScopeId with CART_TOKEN extracts from RequestParam`() {
        val method = TestController::class.java.getMethod(
            "withRequestParam",
            UUID::class.java,
            String::class.java
        )
        val token = UUID.randomUUID()
        val args = arrayOf<Any>(token, "request-data")

        val result = IdempotencyKeyExtractor.extractScopeId(
            method,
            args,
            IdempotencyScopeType.CART_TOKEN
        )

        assertEquals(token.toString(), result)
    }

    @Test
    fun `extractScopeId with CART_TOKEN falls back to cookie when param not found`() {
        val method = TestController::class.java.getMethod(
            "withCookie",
            UUID::class.java,
            String::class.java
        )
        val cartToken = UUID.randomUUID()
        val args = arrayOf<Any>(cartToken, "request-data")

        val result = IdempotencyKeyExtractor.extractScopeId(
            method,
            args,
            IdempotencyScopeType.CART_TOKEN
        )

        assertEquals(cartToken.toString(), result)
    }

    @Test
    fun `extractScopeId with PLATFORM_ID extracts from header`() {
        val method = TestController::class.java.getMethod(
            "withCustomHeader",
            UUID::class.java,
            String::class.java
        )
        val platformId = UUID.randomUUID()
        val args = arrayOf<Any>(platformId, "request-data")

        val result = IdempotencyKeyExtractor.extractScopeId(
            method,
            args,
            IdempotencyScopeType.PLATFORM_ID
        )

        assertEquals(platformId.toString(), result)
    }

    @Test
    fun `extractScopeId with BOOKING_ID extracts from path variable`() {
        val method = TestController::class.java.getMethod(
            "withPathVariable",
            UUID::class.java,
            String::class.java
        )
        val bookingId = UUID.randomUUID()
        val args = arrayOf<Any>(bookingId, "request-data")

        val result = IdempotencyKeyExtractor.extractScopeId(
            method,
            args,
            IdempotencyScopeType.BOOKING_ID
        )

        assertEquals(bookingId.toString(), result)
    }

    @Test
    fun `extractScopeId with CUSTOM uses custom parameter name`() {
        val method = TestController::class.java.getMethod(
            "withCustomParam",
            String::class.java,
            String::class.java
        )
        val customValue = "custom-scope-123"
        val args = arrayOf<Any>(customValue, "request-data")

        val result = IdempotencyKeyExtractor.extractScopeId(
            method,
            args,
            IdempotencyScopeType.CUSTOM,
            "customScope"
        )

        assertEquals(customValue, result)
    }

    @Test
    fun `extractScopeId with NONE returns null`() {
        val method = TestController::class.java.getMethod(
            "withRequestParam",
            UUID::class.java,
            String::class.java
        )
        val args = arrayOf<Any>(UUID.randomUUID(), "request-data")

        val result = IdempotencyKeyExtractor.extractScopeId(
            method,
            args,
            IdempotencyScopeType.NONE
        )

        assertNull(result)
    }

    // ===========================================
    // TEST CONTROLLER (for reflection testing)
    // ===========================================

    @Suppress("unused")
    class TestController {

        fun withIdempotencyKey(
            @RequestHeader("Idempotency-Key") idempotencyKey: String?,
            @RequestBody request: String
        ): String = "response"

        fun withoutIdempotencyKey(
            @RequestBody request: String
        ): String = "response"

        fun withCustomHeader(
            @RequestHeader("X-Platform-ID") platformId: UUID,
            @RequestBody request: String
        ): String = "response"

        fun withRequestParam(
            @RequestParam("token") token: UUID?,
            @RequestBody request: String
        ): String = "response"

        fun withPathVariable(
            @PathVariable("id") id: UUID,
            @RequestBody request: String
        ): String = "response"

        fun withCookie(
            @CookieValue("cart_token") cartToken: UUID?,
            @RequestBody request: String
        ): String = "response"

        fun withCustomParam(
            @RequestParam("customScope") customScope: String,
            @RequestBody request: String
        ): String = "response"
    }
}

