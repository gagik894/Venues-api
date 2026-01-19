package app.venues.shared.idempotency

import app.venues.shared.idempotency.annotation.Idempotent
import app.venues.shared.idempotency.aspect.IdempotentAspect
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.reflect.MethodSignature
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam

/**
 * Edge case tests for request body hash computation in idempotency.
 */
class IdempotencyHashEdgeCaseTest {

    private val idempotencyService: IdempotencyService = mockk(relaxed = true)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val aspect = IdempotentAspect(idempotencyService, objectMapper)

    private data class EmptyRequest(val value: String? = null)
    private data class DummyResponse(val value: String)
    private data class LargeRequest(val data: String)

    private class SampleController {
        @Idempotent(endpoint = "test:no-body", keyPrefix = "test", scopeType = IdempotencyScopeType.CART_TOKEN)
        fun methodWithoutRequestBody(
            @RequestHeader("Idempotency-Key") idempotencyKey: String,
            @RequestParam("token") token: String
        ): ResponseEntity<DummyResponse> {
            return ResponseEntity.ok(DummyResponse("ok"))
        }

        @Idempotent(endpoint = "test:with-body", keyPrefix = "test", scopeType = IdempotencyScopeType.CART_TOKEN)
        fun methodWithRequestBody(
            @RequestHeader("Idempotency-Key") idempotencyKey: String,
            @RequestBody body: EmptyRequest,
            @RequestParam("token") token: String
        ): ResponseEntity<DummyResponse> {
            return ResponseEntity.ok(DummyResponse("ok"))
        }

        @Idempotent(endpoint = "test:null-body", keyPrefix = "test", scopeType = IdempotencyScopeType.CART_TOKEN)
        fun methodWithNullableRequestBody(
            @RequestHeader("Idempotency-Key") idempotencyKey: String,
            @RequestBody body: EmptyRequest?,
            @RequestParam("token") token: String
        ): ResponseEntity<DummyResponse> {
            return ResponseEntity.ok(DummyResponse("ok"))
        }

        @Idempotent(endpoint = "test:large-body", keyPrefix = "test", scopeType = IdempotencyScopeType.CART_TOKEN)
        fun methodWithLargeBody(
            @RequestHeader("Idempotency-Key") idempotencyKey: String,
            @RequestBody body: LargeRequest,
            @RequestParam("token") token: String
        ): ResponseEntity<DummyResponse> {
            return ResponseEntity.ok(DummyResponse("ok"))
        }
    }

    @Test
    fun `computes null hash when no RequestBody parameter exists`() {
        val controllerMethod = SampleController::class.java.getMethod(
            "methodWithoutRequestBody",
            String::class.java,
            String::class.java
        )
        val annotation = controllerMethod.getAnnotation(Idempotent::class.java)
        val joinPoint = mockk<ProceedingJoinPoint>(relaxed = true)
        val signature = mockk<MethodSignature>()
        val contextSlot = slot<IdempotencyContext<*>>()

        every { joinPoint.signature } returns signature
        every { signature.method } returns controllerMethod
        every { joinPoint.args } returns arrayOf("idem-key", "cart-token")
        every { joinPoint.proceed() } returns ResponseEntity.ok(DummyResponse("ok"))
        every { idempotencyService.executeWithIdempotency(capture(contextSlot), any()) } answers {
            IdempotencyService.IdempotencyExecutionResult(secondArg<() -> Any>().invoke(), false)
        }

        aspect.applyIdempotency(joinPoint, annotation)

        // Verify hash is null when no @RequestBody parameter
        assertNull(contextSlot.captured.requestHash, "Hash should be null when no RequestBody")
    }

    @Test
    fun `computes consistent hash for same request body content`() {
        val controllerMethod = SampleController::class.java.getMethod(
            "methodWithRequestBody",
            String::class.java,
            EmptyRequest::class.java,
            String::class.java
        )
        val annotation = controllerMethod.getAnnotation(Idempotent::class.java)
        val contextSlot = mutableListOf<IdempotencyContext<*>>()

        // First request
        val joinPoint1 = mockk<ProceedingJoinPoint>(relaxed = true)
        val signature1 = mockk<MethodSignature>()
        every { joinPoint1.signature } returns signature1
        every { signature1.method } returns controllerMethod
        every { joinPoint1.args } returns arrayOf("idem-1", EmptyRequest("test"), "token")
        every { joinPoint1.proceed() } returns ResponseEntity.ok(DummyResponse("ok"))
        every { idempotencyService.executeWithIdempotency(any<IdempotencyContext<*>>(), any()) } answers {
            contextSlot.add(firstArg())
            IdempotencyService.IdempotencyExecutionResult(secondArg<() -> Any>().invoke(), false)
        }

        aspect.applyIdempotency(joinPoint1, annotation)

        // Second request with same content
        val joinPoint2 = mockk<ProceedingJoinPoint>(relaxed = true)
        val signature2 = mockk<MethodSignature>()
        every { joinPoint2.signature } returns signature2
        every { signature2.method } returns controllerMethod
        every { joinPoint2.args } returns arrayOf("idem-2", EmptyRequest("test"), "token")
        every { joinPoint2.proceed() } returns ResponseEntity.ok(DummyResponse("ok"))

        aspect.applyIdempotency(joinPoint2, annotation)

        // Verify both requests generated the same hash
        assertEquals(2, contextSlot.size)
        assertNotNull(contextSlot[0].requestHash)
        assertEquals(
            contextSlot[0].requestHash, contextSlot[1].requestHash,
            "Same request body content should generate same hash"
        )
    }

    @Test
    fun `computes different hash for different request body content`() {
        val controllerMethod = SampleController::class.java.getMethod(
            "methodWithRequestBody",
            String::class.java,
            EmptyRequest::class.java,
            String::class.java
        )
        val annotation = controllerMethod.getAnnotation(Idempotent::class.java)
        val contextSlot = mutableListOf<IdempotencyContext<*>>()

        // First request
        val joinPoint1 = mockk<ProceedingJoinPoint>(relaxed = true)
        val signature1 = mockk<MethodSignature>()
        every { joinPoint1.signature } returns signature1
        every { signature1.method } returns controllerMethod
        every { joinPoint1.args } returns arrayOf("idem-1", EmptyRequest("test1"), "token")
        every { joinPoint1.proceed() } returns ResponseEntity.ok(DummyResponse("ok"))
        every { idempotencyService.executeWithIdempotency(any<IdempotencyContext<*>>(), any()) } answers {
            contextSlot.add(firstArg())
            IdempotencyService.IdempotencyExecutionResult(secondArg<() -> Any>().invoke(), false)
        }

        aspect.applyIdempotency(joinPoint1, annotation)

        // Second request with different content
        val joinPoint2 = mockk<ProceedingJoinPoint>(relaxed = true)
        val signature2 = mockk<MethodSignature>()
        every { joinPoint2.signature } returns signature2
        every { signature2.method } returns controllerMethod
        every { joinPoint2.args } returns arrayOf("idem-2", EmptyRequest("test2"), "token")
        every { joinPoint2.proceed() } returns ResponseEntity.ok(DummyResponse("ok"))

        aspect.applyIdempotency(joinPoint2, annotation)

        // Verify different requests generated different hashes
        assertEquals(2, contextSlot.size)
        assertNotNull(contextSlot[0].requestHash)
        assertNotNull(contextSlot[1].requestHash)
        assertNotEquals(
            contextSlot[0].requestHash, contextSlot[1].requestHash,
            "Different request body content should generate different hash"
        )
    }

    @Test
    fun `handles null request body gracefully`() {
        val controllerMethod = SampleController::class.java.getMethod(
            "methodWithNullableRequestBody",
            String::class.java,
            EmptyRequest::class.java,
            String::class.java
        )
        val annotation = controllerMethod.getAnnotation(Idempotent::class.java)
        val joinPoint = mockk<ProceedingJoinPoint>(relaxed = true)
        val signature = mockk<MethodSignature>()
        val contextSlot = slot<IdempotencyContext<*>>()

        every { joinPoint.signature } returns signature
        every { signature.method } returns controllerMethod
        every { joinPoint.args } returns arrayOf("idem-key", null, "cart-token")  // null body
        every { joinPoint.proceed() } returns ResponseEntity.ok(DummyResponse("ok"))
        every { idempotencyService.executeWithIdempotency(capture(contextSlot), any()) } answers {
            IdempotencyService.IdempotencyExecutionResult(secondArg<() -> Any>().invoke(), false)
        }

        // Should not throw exception
        assertDoesNotThrow {
            aspect.applyIdempotency(joinPoint, annotation)
        }

        // Hash should be computed for null (serialized as "null")
        assertNotNull(contextSlot.captured.requestHash, "Should compute hash even for null body")
    }

    @Test
    fun `handles large request bodies efficiently`() {
        val controllerMethod = SampleController::class.java.getMethod(
            "methodWithLargeBody",
            String::class.java,
            LargeRequest::class.java,
            String::class.java
        )
        val annotation = controllerMethod.getAnnotation(Idempotent::class.java)
        val joinPoint = mockk<ProceedingJoinPoint>(relaxed = true)
        val signature = mockk<MethodSignature>()
        val contextSlot = slot<IdempotencyContext<*>>()

        // Create a large request body (1MB of data)
        val largeData = "x".repeat(1024 * 1024)
        val largeRequest = LargeRequest(largeData)

        every { joinPoint.signature } returns signature
        every { signature.method } returns controllerMethod
        every { joinPoint.args } returns arrayOf("idem-key", largeRequest, "cart-token")
        every { joinPoint.proceed() } returns ResponseEntity.ok(DummyResponse("ok"))
        every { idempotencyService.executeWithIdempotency(capture(contextSlot), any()) } answers {
            IdempotencyService.IdempotencyExecutionResult(secondArg<() -> Any>().invoke(), false)
        }

        val startTime = System.currentTimeMillis()
        aspect.applyIdempotency(joinPoint, annotation)
        val duration = System.currentTimeMillis() - startTime

        // Verify hash was computed
        assertNotNull(contextSlot.captured.requestHash)
        assertEquals(64, contextSlot.captured.requestHash?.length, "SHA-256 hash should be 64 chars")

        // Verify performance (should be fast even for 1MB)
        assertTrue(duration < 100, "Hashing 1MB should take less than 100ms, took ${duration}ms")
    }

    @Test
    fun `empty request body produces valid hash`() {
        val controllerMethod = SampleController::class.java.getMethod(
            "methodWithRequestBody",
            String::class.java,
            EmptyRequest::class.java,
            String::class.java
        )
        val annotation = controllerMethod.getAnnotation(Idempotent::class.java)
        val joinPoint = mockk<ProceedingJoinPoint>(relaxed = true)
        val signature = mockk<MethodSignature>()
        val contextSlot = slot<IdempotencyContext<*>>()

        every { joinPoint.signature } returns signature
        every { signature.method } returns controllerMethod
        every { joinPoint.args } returns arrayOf("idem-key", EmptyRequest(), "cart-token")  // Empty object
        every { joinPoint.proceed() } returns ResponseEntity.ok(DummyResponse("ok"))
        every { idempotencyService.executeWithIdempotency(capture(contextSlot), any()) } answers {
            IdempotencyService.IdempotencyExecutionResult(secondArg<() -> Any>().invoke(), false)
        }

        aspect.applyIdempotency(joinPoint, annotation)

        // Verify hash is computed for empty object
        assertNotNull(contextSlot.captured.requestHash)
        assertEquals(64, contextSlot.captured.requestHash?.length)
    }
}
