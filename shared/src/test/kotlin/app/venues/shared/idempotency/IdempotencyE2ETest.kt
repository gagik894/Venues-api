package app.venues.shared.idempotency

import app.venues.common.model.ApiResponse
import app.venues.shared.idempotency.annotation.Idempotent
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.*
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * End-to-end controller test demonstrating idempotency behavior.
 *
 * This test shows the complete flow from HTTP request to Redis caching.
 */
@ExtendWith(SpringExtension::class)
@WebMvcTest(controllers = [IdempotencyE2ETest.TestController::class])
@ContextConfiguration(classes = [IdempotencyE2ETest.TestConfig::class])
class IdempotencyE2ETest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var redisTemplate: RedisTemplate<String, String>

    @Autowired
    private lateinit var valueOps: ValueOperations<String, String>

    @Autowired
    private lateinit var testController: TestController

    @Test
    fun `same idempotency key returns same result without re-execution`() {
        val idempotencyKey = UUID.randomUUID().toString()
        val request = TestRequest("test-data")
        val requestJson = objectMapper.writeValueAsString(request)

        // Mock Redis to return null (no cache)
        every { valueOps.get(any()) } returns null
        every { valueOps.setIfAbsent(any(), any(), any<Duration>()) } returns true
        every { valueOps.set(any(), any(), any<Duration>()) } returns Unit
        every { redisTemplate.delete(any<String>()) } returns true

        // First request
        val response1 = mockMvc.perform(
            post("/test/idempotent")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString

        val result1 = objectMapper.readValue(response1, ApiResponse::class.java)

        // Second request with same key - should return cached
        val cachedJson = objectMapper.writeValueAsString(result1)
        every { valueOps.get(any()) } returns cachedJson

        val response2 = mockMvc.perform(
            post("/test/idempotent")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString

        val result2 = objectMapper.readValue(response2, ApiResponse::class.java)

        // Results should be identical
        assertEquals(result1.toString(), result2.toString())

        // Service method should only be called once
        assertEquals(1, testController.executionCount.get())
    }

    @Test
    fun `different idempotency keys execute separately`() {
        val key1 = UUID.randomUUID().toString()
        val key2 = UUID.randomUUID().toString()
        val request = TestRequest("test-data")
        val requestJson = objectMapper.writeValueAsString(request)

        every { valueOps.get(any()) } returns null
        every { valueOps.setIfAbsent(any(), any(), any<Duration>()) } returns true
        every { valueOps.set(any(), any(), any<Duration>()) } returns Unit
        every { redisTemplate.delete(any<String>()) } returns true

        testController.executionCount.set(0)

        // Request with key1
        mockMvc.perform(
            post("/test/idempotent")
                .header("Idempotency-Key", key1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        ).andExpect(status().isOk)

        // Request with key2
        mockMvc.perform(
            post("/test/idempotent")
                .header("Idempotency-Key", key2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        ).andExpect(status().isOk)

        // Both should execute
        assertEquals(2, testController.executionCount.get())
    }

    @Test
    fun `missing idempotency key executes without caching`() {
        val request = TestRequest("test-data")
        val requestJson = objectMapper.writeValueAsString(request)

        testController.executionCount.set(0)

        // Request without idempotency key
        mockMvc.perform(
            post("/test/idempotent")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        ).andExpect(status().isOk)

        // Second request without idempotency key
        mockMvc.perform(
            post("/test/idempotent")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        ).andExpect(status().isOk)

        // Both should execute (no caching)
        assertEquals(2, testController.executionCount.get())

        // Redis should not be touched
        verify(exactly = 0) { valueOps.get(any()) }
    }

    // ===========================================
    // TEST CONFIGURATION
    // ===========================================

    @Configuration
    @EnableAspectJAutoProxy
    class TestConfig {

        @Bean
        fun objectMapper(): ObjectMapper = ObjectMapper()

        @Bean
        fun redisTemplate(): RedisTemplate<String, String> = mockk(relaxed = true)

        @Bean
        fun valueOps(redisTemplate: RedisTemplate<String, String>): ValueOperations<String, String> {
            val ops = mockk<ValueOperations<String, String>>(relaxed = true)
            every { redisTemplate.opsForValue() } returns ops
            return ops
        }

        @Bean
        fun idempotencyService(
            redisTemplate: RedisTemplate<String, String>,
            objectMapper: ObjectMapper
        ): IdempotencyService {
            return IdempotencyService(redisTemplate, objectMapper)
        }

        @Bean
        fun idempotentAspect(idempotencyService: IdempotencyService): app.venues.shared.idempotency.aspect.IdempotentAspect {
            return app.venues.shared.idempotency.aspect.IdempotentAspect(idempotencyService)
        }

        @Bean
        fun testController(): TestController = TestController()
    }

    // ===========================================
    // TEST CONTROLLER
    // ===========================================

    @RestController
    @RequestMapping("/test")
    class TestController {

        val executionCount = AtomicInteger(0)

        @Idempotent(
            endpoint = "test:operation",
            keyPrefix = "test",
            scopeType = IdempotencyScopeType.NONE
        )
        @PostMapping("/idempotent")
        fun idempotentEndpoint(
            @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
            @RequestBody request: TestRequest
        ): ApiResponse<TestResponse> {
            executionCount.incrementAndGet()
            return ApiResponse.success(
                TestResponse(
                    value = "executed",
                    executionCount = executionCount.get(),
                    timestamp = System.currentTimeMillis()
                ),
                "OK"
            )
        }
    }

    data class TestRequest(val data: String)
    data class TestResponse(
        val value: String,
        val executionCount: Int,
        val timestamp: Long
    )
}

