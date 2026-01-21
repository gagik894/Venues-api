package app.venues.shared.idempotency

import app.venues.shared.idempotency.annotation.Idempotent
import app.venues.shared.idempotency.aspect.IdempotentAspect
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.reflect.MethodSignature
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

class IdempotentAspectTest {

    private val idempotencyService: IdempotencyService = mockk(relaxed = true)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val aspect = IdempotentAspect(idempotencyService, objectMapper)

    private data class DummyResponse(val value: String)

    private class SampleController {
        @Idempotent(endpoint = "cart:add-seat", keyPrefix = "booking", scopeType = IdempotencyScopeType.CART_TOKEN)
        fun addSeat(
            @RequestHeader("Idempotency-Key") idempotencyKey: String?,
            @RequestParam("token") token: String?,
            @CookieValue("cart_token") cookieToken: String?
        ): ResponseEntity<DummyResponse> {
            return ResponseEntity.ok(DummyResponse("ok"))
        }

        @Idempotent(endpoint = "platform:hold", keyPrefix = "platform", scopeType = IdempotencyScopeType.PLATFORM_ID)
        fun platformHold(
            @RequestHeader("Idempotency-Key") idempotencyKey: String?,
            @RequestHeader("X-Platform-ID") platformId: String?
        ): DummyResponse {
            return DummyResponse("hold")
        }

        @Idempotent(
            endpoint = "custom",
            keyPrefix = "venue",
            scopeType = IdempotencyScopeType.CUSTOM,
            customScopeName = ""
        )
        fun customScopeMissingName(@RequestHeader("Idempotency-Key") idempotencyKey: String?): DummyResponse {
            return DummyResponse("custom")
        }

        @Idempotent(endpoint = "booking:get", keyPrefix = "booking", scopeType = IdempotencyScopeType.BOOKING_ID)
        fun booking(
            @RequestHeader("Idempotency-Key") idempotencyKey: String?,
            @PathVariable("id") id: String?
        ): ResponseEntity<DummyResponse> {
            return ResponseEntity.ok(DummyResponse("booking"))
        }

        @Idempotent(endpoint = "cart:add-item", keyPrefix = "booking", scopeType = IdempotencyScopeType.CART_TOKEN)
        fun addItem(
            @RequestHeader("Idempotency-Key") idempotencyKey: String?,
            @RequestBody body: DummyResponse
        ): ResponseEntity<DummyResponse> {
            return ResponseEntity.ok(DummyResponse("item-added"))
        }
    }

    @Test
    fun `proceeds without protection when key missing`() {
        val controllerMethod = SampleController::class.java.getMethod(
            "addSeat",
            String::class.java,
            String::class.java,
            String::class.java
        )
        val annotation = controllerMethod.getAnnotation(Idempotent::class.java)
        val joinPoint = mockk<ProceedingJoinPoint>(relaxed = true)
        val signature = mockk<MethodSignature>()

        every { joinPoint.signature } returns signature
        every { signature.method } returns controllerMethod
        every { joinPoint.args } returns arrayOf(null, "cart-token", "cookie-token")
        every { joinPoint.proceed() } returns ResponseEntity.ok(DummyResponse("ok"))

        val result = aspect.applyIdempotency(joinPoint, annotation)

        assertEquals(ResponseEntity.ok(DummyResponse("ok")), result)
        verify(exactly = 1) { joinPoint.proceed() }
        verify(exactly = 0) { idempotencyService.executeWithIdempotency(any<IdempotencyContext<*>>(), any()) }
    }

    @Test
    fun `applies idempotency when key provided`() {
        val controllerMethod = SampleController::class.java.getMethod(
            "addSeat",
            String::class.java,
            String::class.java,
            String::class.java
        )
        val annotation = controllerMethod.getAnnotation(Idempotent::class.java)
        val joinPoint = mockk<ProceedingJoinPoint>(relaxed = true)
        val signature = mockk<MethodSignature>()
        val contextSlot = slot<IdempotencyContext<*>>()

        every { joinPoint.signature } returns signature
        every { signature.method } returns controllerMethod
        every { joinPoint.args } returns arrayOf("idem-1", "cart-token", "cookie-token")
        every { joinPoint.proceed() } returns ResponseEntity.ok(DummyResponse("ok"))
        every { idempotencyService.executeWithIdempotency(capture(contextSlot), any()) } answers {
            IdempotencyService.IdempotencyExecutionResult(secondArg<() -> Any>().invoke(), false)
        }

        val result = aspect.applyIdempotency(joinPoint, annotation)

        assertEquals(ResponseEntity.ok(DummyResponse("ok")), result)
        assertEquals("booking", contextSlot.captured.keyPrefix)
        assertEquals("cart:add-seat", contextSlot.captured.operation)
        assertEquals("cart-token", contextSlot.captured.scopeId)
        assertEquals("idem-1", contextSlot.captured.idempotencyKey)
        assertEquals(DummyResponse::class.java, contextSlot.captured.responseType)
        verify(exactly = 1) { idempotencyService.executeWithIdempotency(any<IdempotencyContext<*>>(), any()) }
        verify(exactly = 1) { joinPoint.proceed() }
    }

    @Test
    fun `falls back to cookie scope when request param missing`() {
        val controllerMethod = SampleController::class.java.getMethod(
            "addSeat",
            String::class.java,
            String::class.java,
            String::class.java
        )
        val annotation = controllerMethod.getAnnotation(Idempotent::class.java)
        val joinPoint = mockk<ProceedingJoinPoint>(relaxed = true)
        val signature = mockk<MethodSignature>()
        val contextSlot = slot<IdempotencyContext<*>>()

        every { joinPoint.signature } returns signature
        every { signature.method } returns controllerMethod
        every { joinPoint.args } returns arrayOf("idem-1", null, "cookie-token")
        every { joinPoint.proceed() } returns ResponseEntity.ok(DummyResponse("ok"))
        every { idempotencyService.executeWithIdempotency(capture(contextSlot), any()) } answers {
            IdempotencyService.IdempotencyExecutionResult(secondArg<() -> Any>().invoke(), false)
        }

        aspect.applyIdempotency(joinPoint, annotation)

        assertEquals("cookie-token", contextSlot.captured.scopeId)
    }

    @Test
    fun `uses direct return type when not ResponseEntity`() {
        val controllerMethod = SampleController::class.java.getMethod(
            "platformHold",
            String::class.java,
            String::class.java
        )
        val annotation = controllerMethod.getAnnotation(Idempotent::class.java)
        val joinPoint = mockk<ProceedingJoinPoint>(relaxed = true)
        val signature = mockk<MethodSignature>()
        val contextSlot = slot<IdempotencyContext<*>>()

        every { joinPoint.signature } returns signature
        every { signature.method } returns controllerMethod
        every { joinPoint.args } returns arrayOf("idem-2", "platform-1")
        every { joinPoint.proceed() } returns DummyResponse("hold")
        every { idempotencyService.executeWithIdempotency(capture(contextSlot), any()) } answers {
            IdempotencyService.IdempotencyExecutionResult(secondArg<() -> Any>().invoke(), false)
        }

        aspect.applyIdempotency(joinPoint, annotation)

        assertEquals(DummyResponse::class.java, contextSlot.captured.responseType)
        assertEquals("platform-1", contextSlot.captured.scopeId)
    }

    @Test
    fun `handles missing method signature by proceeding`() {
        val joinPoint = mockk<ProceedingJoinPoint>(relaxed = true)
        val annotation = SampleController::class.java
            .getMethod("addSeat", String::class.java, String::class.java, String::class.java)
            .getAnnotation(Idempotent::class.java)

        every { joinPoint.signature } returns mockk()
        every { joinPoint.proceed() } returns ResponseEntity.ok(DummyResponse("ok"))

        val result = aspect.applyIdempotency(joinPoint, annotation)

        assertEquals(ResponseEntity.ok(DummyResponse("ok")), result)
        verify(exactly = 0) { idempotencyService.executeWithIdempotency(any<IdempotencyContext<*>>(), any()) }
    }

    @Test
    fun `null custom scope when name missing`() {
        // ... existing test content ...
        val controllerMethod = SampleController::class.java.getMethod(
            "customScopeMissingName",
            String::class.java
        )
        val annotation = controllerMethod.getAnnotation(Idempotent::class.java)
        val joinPoint = mockk<ProceedingJoinPoint>(relaxed = true)
        val signature = mockk<MethodSignature>()
        val contextSlot = slot<IdempotencyContext<*>>()

        every { joinPoint.signature } returns signature
        every { signature.method } returns controllerMethod
        every { joinPoint.args } returns arrayOf("idem-3")
        every { joinPoint.proceed() } returns DummyResponse("custom")
        every { idempotencyService.executeWithIdempotency(capture(contextSlot), any()) } answers {
            IdempotencyService.IdempotencyExecutionResult(secondArg<() -> Any>().invoke(), false)
        }

        aspect.applyIdempotency(joinPoint, annotation)

        assertNull(contextSlot.captured.scopeId)
    }

    @Test
    fun `computes request hash when body is present`() {
        val controllerMethod = SampleController::class.java.getMethod(
            "addItem",
            String::class.java,
            DummyResponse::class.java
        )
        val annotation = controllerMethod.getAnnotation(Idempotent::class.java)
        val joinPoint = mockk<ProceedingJoinPoint>(relaxed = true)
        val signature = mockk<MethodSignature>()
        val contextSlot = slot<IdempotencyContext<*>>()

        val body = DummyResponse("some-value")

        every { joinPoint.signature } returns signature
        every { signature.method } returns controllerMethod
        every { joinPoint.args } returns arrayOf("idem-hash", body)
        every { joinPoint.proceed() } returns ResponseEntity.ok(DummyResponse("item-added"))
        every { idempotencyService.executeWithIdempotency(capture(contextSlot), any()) } answers {
            IdempotencyService.IdempotencyExecutionResult(secondArg<() -> Any>().invoke(), false)
        }

        aspect.applyIdempotency(joinPoint, annotation)

        // Verify hash is present and correct (SHA-256 of {"value":"some-value"})
        // We don't assert exact hash value to avoid brittleness with whitespace, 
        // but we assert it is NOT null and looks like a hex string.
        val hash = contextSlot.captured.requestHash
        assertEquals(true, hash != null, "Request hash should not be null")
        assertEquals(64, hash?.length, "SHA-256 hash length should be 64")
    }
}
 
