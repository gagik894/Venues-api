/*
 * Venues API - Government-Sponsored Cultural Venues Portal
 * Copyright (c) 2025 Government Cultural Department
 *
 * JWT Authentication Filter for processing JWT tokens in requests.
 */

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
 * This filter:
 * 1. Extracts the JWT token from the Authorization header
 * 2. Validates the token using JwtService
 * 3. If valid, creates an Authentication object and stores it in SecurityContext
 * 4. If invalid or missing, lets the request continue (other filters will handle it)
 *
 * The Authentication object contains:
 * - Principal: Map with userId, email, role
 * - Authorities: User's role(s) as GrantedAuthority
 * - Details: Request details
 *
 * Flow:
 * ```
 * Request → Extract Token → Validate Token → Create Authentication → Continue Chain
 * ```
 *
 * SecurityContext:
 * - Stores the Authentication for the current request
 * - Thread-safe (uses ThreadLocal)
 * - Cleared after request completes
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
    }

    /**
     * Filters each HTTP request to validate JWT tokens.
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
            // Extract JWT token from Authorization header
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
     * Extracts JWT token from the Authorization header.
     *
     * Expected header format: "Authorization: Bearer <token>"
     *
     * @param request HTTP request
     * @return JWT token string or null if not found or invalid format
     */
    private fun extractJwtFromRequest(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader(AUTHORIZATION_HEADER)

        return if (bearerToken != null && bearerToken.startsWith(BEARER_PREFIX)) {
            bearerToken.substring(BEARER_PREFIX.length)
        } else {
            null
        }
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
