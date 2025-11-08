/*
 * Venues API - Government-Sponsored Cultural Venues Portal
 * Copyright (c) 2025 Government Cultural Department
 *
 * Custom JWT Authentication Entry Point for handling authentication failures.
 */

package app.venues.shared.security.jwt

import app.venues.common.constants.AppConstants
import app.venues.shared.web.exception.ApiErrorResponseBuilder
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import java.io.IOException

/**
 * Custom Authentication Entry Point for JWT authentication failures.
 *
 * This component intercepts authentication failures (401) at the filter level
 * and returns a structured JSON error response using the centralized ApiErrorResponseBuilder.
 *
 * Since filter-level exceptions occur before the request reaches controllers,
 * the GlobalExceptionHandler cannot catch them. This handler provides the same
 * error response format for consistency.
 *
 * Handles:
 * - Missing JWT tokens
 * - Invalid JWT tokens
 * - Expired JWT tokens
 *
 * @see app.venues.shared.web.exception.GlobalExceptionHandler for controller-layer exception handling
 * @see ApiErrorResponseBuilder for error response formatting
 */
class JwtAuthenticationEntryPoint : AuthenticationEntryPoint {

    private val logger = KotlinLogging.logger {}
    private val objectMapper = ObjectMapper()

    /**
     * Handles authentication failures by returning a structured error response.
     *
     * @param request HTTP request that caused authentication failure
     * @param response HTTP response where error will be written
     * @param authException The authentication exception that was thrown
     * @throws IOException if writing response fails
     * @throws ServletException if servlet error occurs
     */
    @Throws(IOException::class, ServletException::class)
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        logger.warn { "Authentication failed: ${authException.message}" }

        // Build standardized error response using shared builder
        val errorResponse = ApiErrorResponseBuilder.buildErrorResponse(
            code = AppConstants.ErrorCode.AUTHENTICATION_FAILED.code,
            message = AppConstants.ErrorCode.AUTHENTICATION_FAILED.message,
            path = request.requestURI
        )

        // Set response properties
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"
        response.status = HttpServletResponse.SC_UNAUTHORIZED

        // Write JSON response
        response.writer.write(objectMapper.writeValueAsString(errorResponse))
    }
}
