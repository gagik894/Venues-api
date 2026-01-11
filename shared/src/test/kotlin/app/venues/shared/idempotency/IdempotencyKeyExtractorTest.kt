package app.venues.shared.idempotency

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import java.lang.reflect.Method

class IdempotencyKeyExtractorTest {

    private class SampleController {
        fun headerExplicit(@RequestHeader("Idempotency-Key") key: String) {}

        fun headerValueAttribute(@RequestHeader(value = "X-Platform-ID") platformId: String) {}

        fun headerFallback(@RequestHeader key: String) {}

        fun requestParamExplicit(@RequestParam("token") token: String) {}

        fun requestParamFallback(@RequestParam token: String) {}

        fun pathVariableExplicit(@PathVariable("bookingId") bookingId: String) {}

        fun pathVariableFallback(@PathVariable bookingId: String) {}

        fun cookieExplicit(@CookieValue("cart_token") token: String) {}

        fun scopeSources(
            @RequestParam("token") token: String?,
            @CookieValue("cart_token") cookieToken: String?,
            @RequestHeader("X-Platform-ID") platformId: String?,
            @PathVariable("id") id: String?
        ) {
        }

        fun customScope(@RequestParam("customScope") custom: String) {}
    }

    private fun method(name: String, vararg params: Class<*>): Method {
        return SampleController::class.java.getMethod(name, *params)
    }

    @Test
    fun `extracts idempotency key header`() {
        val method = method("headerExplicit", String::class.java)
        val result = IdempotencyKeyExtractor.extractIdempotencyKey(method, arrayOf("idem-1"))

        assertEquals("idem-1", result)
    }

    @Test
    fun `extracts header via value attribute`() {
        val method = method("headerValueAttribute", String::class.java)
        val result = IdempotencyKeyExtractor.extractHeaderValue(method, arrayOf("platform-1"), "X-Platform-ID")

        assertEquals("platform-1", result)
    }

    @Test
    fun `falls back to parameter name when header name missing`() {
        val method = method("headerFallback", String::class.java)
        val parameterName = method.parameters.first().name
        val result = IdempotencyKeyExtractor.extractHeaderValue(method, arrayOf("abc"), parameterName)

        assertEquals("abc", result)
    }

    @Test
    fun `extracts request params`() {
        val method = method("requestParamExplicit", String::class.java)
        val result = IdempotencyKeyExtractor.extractRequestParam(method, arrayOf("cart-token"), "token")

        assertEquals("cart-token", result)
    }

    @Test
    fun `falls back to kotlin param name for request param`() {
        val method = method("requestParamFallback", String::class.java)
        val parameterName = method.parameters.first().name
        val result = IdempotencyKeyExtractor.extractRequestParam(method, arrayOf("cart-token"), parameterName)

        assertEquals("cart-token", result)
    }

    @Test
    fun `extracts path variables`() {
        val method = method("pathVariableExplicit", String::class.java)
        val result = IdempotencyKeyExtractor.extractPathVariable(method, arrayOf("booking-1"), "bookingId")

        assertEquals("booking-1", result)
    }

    @Test
    fun `falls back to kotlin param name for path variable`() {
        val method = method("pathVariableFallback", String::class.java)
        val parameterName = method.parameters.first().name
        val result = IdempotencyKeyExtractor.extractPathVariable(method, arrayOf("booking-2"), parameterName)

        assertEquals("booking-2", result)
    }

    @Test
    fun `extracts cookie values`() {
        val method = method("cookieExplicit", String::class.java)
        val result = IdempotencyKeyExtractor.extractCookieValue(method, arrayOf("cookie-token"), "cart_token")

        assertEquals("cookie-token", result)
    }

    @Test
    fun `scopes prefer request param over cookie`() {
        val method = method(
            "scopeSources",
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java
        )
        val args = arrayOf<Any?>("req-token", "cookie-token", null, null) as Array<Any>

        val scope = IdempotencyKeyExtractor.extractScopeId(method, args, IdempotencyScopeType.CART_TOKEN)

        assertEquals("req-token", scope)
    }

    @Test
    fun `scopes fall back to cookie when request param missing`() {
        val method = method(
            "scopeSources",
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java
        )
        val args = arrayOf<Any?>(null, "cookie-token", null, null) as Array<Any>

        val scope = IdempotencyKeyExtractor.extractScopeId(method, args, IdempotencyScopeType.CART_TOKEN)

        assertEquals("cookie-token", scope)
    }

    @Test
    fun `extracts platform scope from header`() {
        val method = method(
            "scopeSources",
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java
        )
        val args = arrayOf<Any?>(null, null, "platform-1", null) as Array<Any>

        val scope = IdempotencyKeyExtractor.extractScopeId(method, args, IdempotencyScopeType.PLATFORM_ID)

        assertEquals("platform-1", scope)
    }

    @Test
    fun `extracts booking scope from path variable`() {
        val method = method(
            "scopeSources",
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java
        )
        val args = arrayOf<Any?>(null, null, null, "booking-3") as Array<Any>

        val scope = IdempotencyKeyExtractor.extractScopeId(method, args, IdempotencyScopeType.BOOKING_ID)

        assertEquals("booking-3", scope)
    }

    @Test
    fun `extracts custom scope from request param`() {
        val method = method("customScope", String::class.java)
        val args = arrayOf<Any>("custom-1")

        val scope = IdempotencyKeyExtractor.extractScopeId(
            method,
            args,
            IdempotencyScopeType.CUSTOM,
            "customScope"
        )

        assertEquals("custom-1", scope)
    }

    @Test
    fun `returns null when scope type is none`() {
        val method = method("headerExplicit", String::class.java)
        val scope = IdempotencyKeyExtractor.extractScopeId(
            method,
            arrayOf<Any>("value"),
            IdempotencyScopeType.NONE
        )

        assertNull(scope)
    }
}
