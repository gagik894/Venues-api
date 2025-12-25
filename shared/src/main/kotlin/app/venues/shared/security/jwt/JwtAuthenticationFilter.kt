package app.venues.shared.security.jwt

import io.github.oshai.kotlinlogging.KotlinLogging
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Filter that intercepts all HTTP requests to validate JWT tokens.
 *
 * This filter supports dual authentication methods:
 * 1. Authorization header (Bearer tokens) - for API clients, mobile apps, SPAs
 * 2. HttpOnly cookies - for browser-based clients (admin panels, customer portals)
 *
 * Authentication flow:
 * ```
 * Request → Extract Token (header OR cookie) → Validate Token → Create Authentication → Continue Chain
 * ```
 *
 * Extraction priority:
 * 1. Authorization header: "Authorization: Bearer <token>"
 * 2. staff_auth_token cookie (staff/admin sessions)
 * 3. user_auth_token cookie (customer sessions)
 *
 * Why dual authentication?
 * - **Headers**: Best for programmatic clients (mobile apps, API integrations)
 * - **Cookies**: Best for browsers (XSS protection via HttpOnly, CSRF protection via SameSite)
 *
 * The Authentication object contains:
 * - Principal: Map with userId, email, role
 * - Authorities: User's role(s) as GrantedAuthority
 * - Details: Request details
 *
 * Request attributes (for controller access):
 * - principalId: UUID of the authenticated principal (user/staff/venue)
 * - staffId: Alias for principalId (used in staff controllers)
 * - userId: Alias for principalId (used in user controllers)
 *
 * SecurityContext:
 * - Stores the Authentication for the current request
 * - Thread-safe (uses ThreadLocal)
 * - Cleared after request completes
 *
 * Error handling:
 * - Invalid/expired tokens: logged and ignored (request continues without authentication)
 * - Protected endpoints: JwtAuthenticationEntryPoint returns 401 Unauthorized
 *
 * @property jwtService Service for JWT operations
 */
@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService
) : OncePerRequestFilter() {

    private val log = KotlinLogging.logger {}

    companion object {
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "

        // Cookie names for JWT tokens (supports multiple auth contexts)
        private const val STAFF_AUTH_COOKIE = "staff_auth_token"
        private const val USER_AUTH_COOKIE = "user_auth_token"
    }

    /**
     * Filters each HTTP request to validate JWT tokens.
     *
     * Extraction order:
     * 1. Authorization header (Bearer token)
     * 2. staff_auth_token cookie
     * 3. user_auth_token cookie
     *
     * This dual extraction strategy supports both:
     * - Programmatic API clients (mobile apps, SPAs) using Authorization headers
     * - Browser-based clients using HttpOnly cookies for XSS protection
     *
     * @param request HTTP request
     * @param response HTTP response
     * @param filterChain Filter chain to continue processing
     */
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            // Extract JWT token from Authorization header OR cookies
            val jwt = extractJwtFromRequest(request)

            // If token exists and authentication is not already set
            if (jwt != null && SecurityContextHolder.getContext().authentication == null) {
                authenticateWithJwt(jwt, request)
            }
        } catch (e: Exception) {
            log.warn(e) { "Cannot set user authentication: ${e.message}" }
            // Note: We let the filter chain continue. If authentication is required,
            // the JwtAuthenticationEntryPoint will handle the failure later.
        }

        // Continue with the filter chain
        filterChain.doFilter(request, response)
    }

    /**
     * Extracts JWT token from Authorization header or cookies.
     *
     * Extraction priority:
     * 1. Authorization header: "Authorization: Bearer <token>"
     * 2. staff_auth_token cookie (for admin/staff browser sessions)
     * 3. user_auth_token cookie (for customer browser sessions)
     *
     * This approach enables:
     * - Mobile apps and SPAs to use Authorization headers
     * - Browser-based admin panels to use HttpOnly cookies (XSS protection)
     * - Secure cookie-based sessions without exposing tokens to JavaScript
     *
     * @param request HTTP request
     * @return JWT token string or null if not found in header or cookies
     */
    private fun extractJwtFromRequest(request: HttpServletRequest): String? {
        // 1. Try Authorization header first (Bearer token)
        val bearerToken = request.getHeader(AUTHORIZATION_HEADER)
        if (bearerToken != null && bearerToken.startsWith(BEARER_PREFIX)) {
            log.debug { "JWT extracted from Authorization header" }
            return bearerToken.substring(BEARER_PREFIX.length)
        }

        // 2. Try staff_auth_token cookie
        val cookies = request.cookies
        if (cookies != null) {
            val staffCookie = cookies.firstOrNull { it.name == STAFF_AUTH_COOKIE }
            if (staffCookie != null && staffCookie.value.isNotEmpty()) {
                log.debug { "JWT extracted from staff_auth_token cookie" }
                return staffCookie.value
            }

            // 3. Try user_auth_token cookie
            val userCookie = cookies.firstOrNull { it.name == USER_AUTH_COOKIE }
            if (userCookie != null && userCookie.value.isNotEmpty()) {
                log.debug { "JWT extracted from user_auth_token cookie" }
                return userCookie.value
            }
        }

        log.debug { "No JWT found in Authorization header or cookies" }
        return null
    }

    /**
     * Validates JWT token and sets authentication in SecurityContext.
     *
     * Extracts user information from the token and creates an Authentication object
     * with the user's ID, email, and role as authorities.
     *
     * @param jwt JWT token string
     * @param request HTTP request for additional details
     */
    private fun authenticateWithJwt(jwt: String, request: HttpServletRequest) {
        try {
            // --- OPTIMIZATION ---
            // Parse the token ONCE to get all claims
            val claims = jwtService.getAllClaimsFromToken(jwt)

            // Check if token is expired (this is already checked by getAllClaimsFromToken,
            // but we can double-check the claim just in case)
            if (jwtService.isTokenExpired(claims)) {
                log.debug { "JWT token is expired" }
                return
            }

            // Extract principal information from the claims
            val principalId = jwtService.getIdFromClaims(claims)
            val email = jwtService.getEmailFromClaims(claims)
            val role = jwtService.getRoleFromClaims(claims)

            // Create authorities from role
            val authorities = listOf(SimpleGrantedAuthority("ROLE_$role"))

            // Create principal as a Map containing principal info
            // Using "id" (not "userId") to be generic - works for users, venues, companies, etc.
            val principal = mapOf(
                "id" to principalId,
                "email" to email,
                "role" to role
            )

            // Create authentication token
            val authentication = UsernamePasswordAuthenticationToken(
                principal,
                null, // No credentials needed after authentication
                authorities
            )

            // Add request details to authentication
            authentication.details = WebAuthenticationDetailsSource().buildDetails(request)

            // Set authentication in SecurityContext
            SecurityContextHolder.getContext().authentication = authentication

            // Set principal ID as request attributes for controller access via @RequestAttribute.
            // IMPORTANT: do NOT set both staffId and userId, otherwise audits (and controllers) cannot
            // distinguish staff-driven vs user-driven operations.
            request.setAttribute("principalId", principalId)
            when (role.uppercase()) {
                "STAFF", "SUPER_ADMIN" -> request.setAttribute("staffId", principalId)
                "USER" -> request.setAttribute("userId", principalId)
            }
            log.debug { "Principal authenticated successfully: principalId=$principalId, email=$email, role=$role" }

        } catch (e: ExpiredJwtException) {
            log.warn(e) { "JWT token is expired: ${e.message}" }
        } catch (e: JwtException) {
            // Catches other JWT-related errors (invalid signature, malformed, etc.)
            log.warn(e) { "Invalid JWT token: ${e.message}" }
        } catch (e: IllegalArgumentException) {
            // Catches errors from our custom claim parsing (e.g., missing 'id' or 'role')
            log.warn(e) { "Failed to parse custom claims from JWT: ${e.message}" }
        } catch (e: Exception) {
            log.warn(e) { "Failed to authenticate with JWT: ${e.message}" }
        }
        // If any exception occurs, we simply don't set the authentication
        // and let the request proceed. The SecurityFilterChain will catch
        // the lack of authentication later if the endpoint is secured.
    }
}
