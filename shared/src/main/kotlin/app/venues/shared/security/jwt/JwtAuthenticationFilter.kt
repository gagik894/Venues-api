/*
 * Venues API - Government-Sponsored Cultural Venues Portal
 * Copyright (c) 2025 Government Cultural Department
 *
 * JWT Authentication Filter for processing JWT tokens in requests.
 */

package app.venues.shared.security.jwt

import io.github.oshai.kotlinlogging.KotlinLogging
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

            // If token exists and is valid, set authentication in SecurityContext
            if (jwt != null) {
                authenticateWithJwt(jwt, request)
            }
        } catch (e: Exception) {
            log.warn(e) { "Cannot set user authentication: ${e.message}" }
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
            // Extract principal information from JWT
            val principalId = jwtService.getIdFromToken(jwt)
            val email = jwtService.getEmailFromToken(jwt)
            val role = jwtService.getRoleFromToken(jwt)

            // Check if token is expired
            if (jwtService.isTokenExpired(jwt)) {
                log.debug { "JWT token is expired for principal: $email" }
                return
            }

            // Create authorities from role
            val authorities = listOf(SimpleGrantedAuthority("ROLE_$role"))

            // Create principal as a Map containing user info
            // This makes it easy to extract userId in SecurityUtil
            val principal = mapOf(
                "userId" to principalId,
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
        } catch (e: Exception) {
            log.warn(e) { "Failed to authenticate with JWT: ${e.message}" }
            // Don't throw exception - let other filters handle it
        }
    }
}

