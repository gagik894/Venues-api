package app.venues.shared.idempotency

import app.venues.shared.idempotency.annotation.Idempotent
import app.venues.shared.idempotency.aspect.IdempotentAspect
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.*
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.reflect.MethodSignature
import org.junit.jupiter.api.Test
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam

/**
 * Integration tests for X-Idempotency-Cache header functionality.
 */
class IdempotencyCacheHeaderTest {

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
    }

    @Test
    fun `sets X-Idempotency-Cache header to MISS when executing fresh`() {
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
        every { joinPoint.args } returns arrayOf("idem-header-miss", "cart-token", "cookie-token")
        every { joinPoint.proceed() } returns ResponseEntity.ok(DummyResponse("ok"))
        every { idempotencyService.executeWithIdempotency(any<IdempotencyContext<*>>(), any()) } answers {
            // Simulate fresh execution (not from cache)
            IdempotencyService.IdempotencyExecutionResult(secondArg<() -> Any>().invoke(), isFromCache = false)
        }

        // Mock request context for header setting
        val requestAttributes = mockk<org.springframework.web.context.request.ServletRequestAttributes>(relaxed = true)
        val httpResponse = mockk<jakarta.servlet.http.HttpServletResponse>(relaxed = true)
        every { requestAttributes.response } returns httpResponse
        mockkStatic(org.springframework.web.context.request.RequestContextHolder::class)
        every { org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() } returns requestAttributes

        aspect.applyIdempotency(joinPoint, annotation)

        verify { httpResponse.setHeader("X-Idempotency-Cache", "MISS") }

        unmockkStatic(org.springframework.web.context.request.RequestContextHolder::class)
    }

    @Test
    fun `sets X-Idempotency-Cache header to HIT when returning cached result`() {
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
        every { joinPoint.args } returns arrayOf("idem-header-hit", "cart-token", "cookie-token")
        every { joinPoint.proceed() } returns ResponseEntity.ok(DummyResponse("ok"))
        every { idempotencyService.executeWithIdempotency(any<IdempotencyContext<*>>(), any()) } returns
                // Simulate returning cached result
                IdempotencyService.IdempotencyExecutionResult(DummyResponse("cached"), isFromCache = true)

        // Mock request context for header setting
        val requestAttributes = mockk<org.springframework.web.context.request.ServletRequestAttributes>(relaxed = true)
        val httpResponse = mockk<jakarta.servlet.http.HttpServletResponse>(relaxed = true)
        every { requestAttributes.response } returns httpResponse
        mockkStatic(org.springframework.web.context.request.RequestContextHolder::class)
        every { org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() } returns requestAttributes

        aspect.applyIdempotency(joinPoint, annotation)

        verify { httpResponse.setHeader("X-Idempotency-Cache", "HIT") }

        unmockkStatic(org.springframework.web.context.request.RequestContextHolder::class)
    }
}
