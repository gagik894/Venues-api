/*
 * Venues API - Government-Sponsored Cultural Venues Portal
 * Copyright (c) 2025 Government Cultural Department
 *
 * Utility for creating standardized error responses.
 */

package app.venues.shared.web.exception

import app.venues.common.model.ApiErrorResponse
import app.venues.common.model.ErrorDetail
import kotlinx.datetime.Clock
import java.util.*

/**
 * Utility for creating standardized error responses.
 *
 * This utility ensures consistent error response formatting across both:
 * - Controller layer (via GlobalExceptionHandler)
 * - Filter layer (via JWT security handlers)
 *
 * Single source of truth for error response format.
 */
object ApiErrorResponseBuilder {

    /**
     * Creates a standardized error response.
     *
     * @param code Error code (from AppConstants)
     * @param message Human-readable error message
     * @param path Request path that caused the error
     * @param details Optional structured error details (e.g., field validation errors)
     * @return ApiErrorResponse ready to be sent to client
     */
    fun buildErrorResponse(
        code: String,
        message: String,
        path: String,
        details: Map<String, List<String>>? = null
    ): ApiErrorResponse {
        return ApiErrorResponse(
            success = false,
            error = ErrorDetail(
                code = code,
                message = message,
                details = details
            ),
            timestamp = Clock.System.now().toString(),
            path = path,
            traceId = UUID.randomUUID().toString()
        )
    }
}

