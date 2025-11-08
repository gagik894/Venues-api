/*
 * Venues API - Government-Sponsored Cultural Venues Portal
 * Copyright (c) 2025 Government Cultural Department
 *
 * Custom JWT Access Denied Handler for handling authorization failures.
 */

package app.venues.shared.security.jwt

import app.venues.common.constants.AppConstants
import app.venues.shared.web.exception.ApiErrorResponseBuilder
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import java.io.IOException

/**
 * Custom Access Denied Handler for JWT authorization failures.
 *
 * This component intercepts authorization failures (403) at the filter level
 * and returns a structured JSON error response using the centralized ApiErrorResponseBuilder.
 *
 * Since filter-level exceptions occur before the request reaches controllers,
 * the GlobalExceptionHandler cannot catch them. This handler provides the same
 * error response format for consistency.
 *
 * Handles:
 * - Missing required roles
 * - Insufficient permissions
 * - Access to restricted endpoints
 *
 * @see app.venues.shared.web.exception.GlobalExceptionHandler for controller-layer exception handling
 * @see ApiErrorResponseBuilder for error response formatting
 */
class JwtAccessDeniedHandler : AccessDeniedHandler {

    private val logger = KotlinLogging.logger {}
    private val objectMapper = ObjectMapper()

    /**
     * Handles authorization failures by returning a structured error response.
     *
     * @param request HTTP request that caused authorization failure
     * @param response HTTP response where error will be written
     * @param accessDeniedException The access denied exception that was thrown
     * @throws IOException if writing response fails
     * @throws ServletException if servlet error occurs
     */
    @Throws(IOException::class, ServletException::class)
    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException
    ) {
        logger.warn { "Access denied: ${accessDeniedException.message}" }

        // Build standardized error response using shared builder
        val errorResponse = ApiErrorResponseBuilder.buildErrorResponse(
            code = AppConstants.ErrorCode.AUTHORIZATION_FAILED.code,
            message = AppConstants.ErrorCode.AUTHORIZATION_FAILED.message,
            path = request.requestURI
        )

        // Set response properties
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"
        response.status = HttpServletResponse.SC_FORBIDDEN

        // Write JSON response
        response.writer.write(objectMapper.writeValueAsString(errorResponse))
    }
}

