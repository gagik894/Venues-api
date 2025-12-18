package app.venues.shared.security.filter

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.Refill
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Government-grade rate limiting filter to prevent brute-force attacks and API abuse.
 * Uses Bucket4j for token bucket algorithm.
 */
@Component
class RateLimitingFilter : OncePerRequestFilter() {

    private val buckets = ConcurrentHashMap<String, Bucket>()

    // 100 requests per minute per IP
    private val limit = Bandwidth.classic(100, Refill.greedy(100, Duration.ofMinutes(1)))

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // Skip rate limiting for health checks to avoid false positives in monitoring
        if (request.requestURI.contains("/actuator/health")) {
            filterChain.doFilter(request, response)
            return
        }

        val ip = getClientIp(request)
        val bucket = buckets.computeIfAbsent(ip) {
            Bucket.builder()
                .addLimit(limit)
                .build()
        }

        val probe = bucket.tryConsumeAndReturnRemaining(1)
        if (probe.isConsumed) {
            response.addHeader("X-Rate-Limit-Remaining", probe.remainingTokens.toString())
            filterChain.doFilter(request, response)
        } else {
            logger.warn { "Rate limit exceeded for IP: $ip on ${request.requestURI}" }

            response.status = 429 // Too Many Requests
            response.contentType = "application/json"
            response.addHeader(
                "X-Rate-Limit-Retry-After-Seconds",
                (probe.nanosToWaitForRefill / 1_000_000_000).toString()
            )
            response.writer.write("""{"error": "Rate limit exceeded", "message": "Too many requests. Please try again later."}""")
        }
    }

    private fun getClientIp(request: HttpServletRequest): String {
        val xfHeader = request.getHeader("X-Forwarded-For")
        return if (xfHeader == null) {
            request.remoteAddr
        } else {
            xfHeader.split(",")[0]
        }
    }
}
