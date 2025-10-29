package app.venues.platform.security

import app.venues.common.exception.VenuesException
import app.venues.platform.repository.PlatformRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import java.time.Duration
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Filter to authenticate platform API requests using HMAC signature verification.
 *
 * Verifies:
 * 1. X-Platform-ID header is present and valid
 * 2. X-Platform-Signature header matches HMAC-SHA256(platformId|timestamp|nonce, sharedSecret)
 * 3. X-Platform-Timestamp is recent (within 5 minutes)
 * 4. X-Platform-Nonce is unique (prevents replay attacks via Redis)
 *
 * Authentication Flow:
 * Platform signs request: HMAC-SHA256(platformId|timestamp|nonce, sharedSecret)
 */
@Component
class PlatformAuthenticationFilter(
    private val platformRepository: PlatformRepository,
    private val nonceService: NonceService
) : OncePerRequestFilter() {

    private val logger = KotlinLogging.logger {}

    companion object {
        private const val PLATFORM_ID_HEADER = "X-Platform-ID"
        private const val SIGNATURE_HEADER = "X-Platform-Signature"
        private const val TIMESTAMP_HEADER = "X-Platform-Timestamp"
        private const val NONCE_HEADER = "X-Platform-Nonce"
        private const val PLATFORM_API_PATH = "/api/v1/platforms/"
        private const val MAX_TIMESTAMP_AGE_SECONDS = 300L // 5 minutes
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        // Only filter platform API endpoints
        return !request.requestURI.startsWith(PLATFORM_API_PATH)
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            // Wrap request to allow reading body multiple times
            val wrappedRequest = request as? ContentCachingRequestWrapper ?: ContentCachingRequestWrapper(request)

            // Extract headers
            val platformIdStr = wrappedRequest.getHeader(PLATFORM_ID_HEADER)
                ?: throw VenuesException.AuthenticationFailure("Missing $PLATFORM_ID_HEADER header")

            val signature = wrappedRequest.getHeader(SIGNATURE_HEADER)
                ?: throw VenuesException.AuthenticationFailure("Missing $SIGNATURE_HEADER header")

            val timestamp = wrappedRequest.getHeader(TIMESTAMP_HEADER)
                ?: throw VenuesException.AuthenticationFailure("Missing $TIMESTAMP_HEADER header")

            val nonce = wrappedRequest.getHeader(NONCE_HEADER)
                ?: throw VenuesException.AuthenticationFailure("Missing $NONCE_HEADER header")

            // Parse platform ID
            val platformId = try {
                platformIdStr.toLong()
            } catch (e: NumberFormatException) {
                throw VenuesException.AuthenticationFailure("Invalid platform ID format")
            }

            // Verify platform exists and is active
            val platform = platformRepository.findById(platformId).orElse(null)
                ?: throw VenuesException.AuthenticationFailure("Platform not found")

            if (!platform.isActive()) {
                throw VenuesException.AuthenticationFailure("Platform is not active (status: ${platform.status})")
            }

            // Verify timestamp is recent
            verifyTimestamp(timestamp)

            // Verify nonce is unique
            verifyNonce(nonce, platformId)

            // Read request body (for service layer validation if needed)
            wrappedRequest.inputStream.readBytes().toString(Charsets.UTF_8)

            // Verify signature
            verifySignature(signature, timestamp, nonce, platformId, platform.sharedSecret)

            logger.debug { "Platform ${platform.name} authenticated successfully" }

            // Continue with authenticated request
            filterChain.doFilter(wrappedRequest, response)

        } catch (e: VenuesException) {
            logger.warn { "Platform authentication failed: ${e.message}" }
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = "application/json"
            response.writer.write("""{"success":false,"message":"${e.message}"}""")
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error in platform authentication filter" }
            response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            response.contentType = "application/json"
            response.writer.write("""{"success":false,"message":"Authentication error"}""")
        }
    }

    /**
     * Verify timestamp is within acceptable range
     */
    private fun verifyTimestamp(timestamp: String) {
        try {
            val requestTime = Instant.parse(timestamp)
            val now = Instant.now()
            val age = Duration.between(requestTime, now).abs()

            if (age.seconds > MAX_TIMESTAMP_AGE_SECONDS) {
                throw VenuesException.AuthenticationFailure("Request timestamp too old or in future")
            }
        } catch (e: Exception) {
            throw VenuesException.AuthenticationFailure("Invalid timestamp format")
        }
    }

    /**
     * Verify nonce hasn't been used before (prevents replay attacks).
     * Uses Redis for distributed nonce tracking across multiple instances.
     */
    private fun verifyNonce(nonce: String, platformId: Long) {
        val isUsed = nonceService.isNonceUsed(nonce, platformId)
        if (isUsed) {
            throw VenuesException.AuthenticationFailure("Nonce already used (replay attack detected)")
        }
    }

    /**
     * Verify HMAC-SHA256 signature
     * Signature format: HMAC-SHA256(platformId|timestamp|nonce, sharedSecret)
     */
    private fun verifySignature(
        providedSignature: String,
        timestamp: String,
        nonce: String,
        platformId: Long,
        sharedSecret: String
    ) {
        val data = "$platformId|$timestamp|$nonce"
        val expectedSignature = generateSignature(data, sharedSecret)

        if (providedSignature != expectedSignature) {
            throw VenuesException.AuthenticationFailure("Invalid signature")
        }
    }

    /**
     * Generate HMAC-SHA256 signature
     */
    private fun generateSignature(data: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(secretKeySpec)
        val hmac = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return hmac.joinToString("") { "%02x".format(it) }
    }
}

