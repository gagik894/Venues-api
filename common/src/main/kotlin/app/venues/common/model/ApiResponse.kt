package app.venues.common.model

import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * Standard API response wrapper for successful operations.
 *
 * Provides a consistent response structure across all API endpoints.
 * This ensures clients can reliably parse responses and extract metadata.
 *
 * @param T The type of data being returned (can be Unit for operations with no data)
 * @property success Indicates if the operation was successful (always true for this class)
 * @property message Human-readable success message
 * @property data The actual response payload (optional - null for operations like delete, password change)
 * @property timestamp ISO-8601 formatted timestamp of the response
 */
@Serializable
data class ApiResponse<T>(
    val success: Boolean = true,
    val message: String = "Operation completed successfully",
    val data: T? = null,
    val timestamp: String
) {
    companion object {
        /**
         * Factory method to create a successful API response with data.
         *
         * @param data The payload to return
         * @param message Optional success message
         * @return ApiResponse wrapping the provided data
         */
        fun <T> success(
            data: T,
            message: String = "Operation completed successfully"
        ): ApiResponse<T> {
            return ApiResponse(
                success = true,
                message = message,
                data = data,
                timestamp = Clock.System.now().toString()
            )
        }

        /**
         * Factory method to create a successful API response without data (message only).
         * Useful for operations like delete, password change, etc.
         *
         * @param message Success message
         * @return ApiResponse with no data payload
         */
        fun success(
            message: String = "Operation completed successfully"
        ): ApiResponse<Unit> {
            return ApiResponse(
                success = true,
                message = message,
                data = null,
                timestamp = Clock.System.now().toString()
            )
        }
    }
}

/**
 * Standard API error response for failed operations.
 *
 * Provides detailed error information to help clients understand
 * what went wrong and how to potentially resolve the issue.
 *
 * @property success Indicates if the operation was successful (always false for errors)
 * @property error Detailed error information
 * @property timestamp ISO-8601 formatted timestamp of the error
 * @property path The API path where the error occurred
 * @property traceId Optional trace ID for error tracking and debugging
 */
@Serializable
data class ApiErrorResponse(
    val success: Boolean = false,
    val error: ErrorDetail,
    val timestamp: String,
    val path: String? = null,
    val traceId: String? = null
)

/**
 * Detailed error information.
 *
 * @property code Application-specific error code
 * @property message Human-readable error message
 * @property details Additional error details or validation failures
 */
@Serializable
data class ErrorDetail(
    val code: String,
    val message: String,
    val details: Map<String, List<String>>? = null
)

/**
 * Paginated response wrapper combining data and pagination information.
 *
 * @param T The type of items in the paginated list
 * @property items List of items for the current page
 * @property page Current page number (0-indexed)
 * @property size Number of items per page
 * @property totalElements Total number of items available
 * @property totalPages Total number of pages
 * @property hasNext Indicates if there's a next page
 * @property hasPrevious Indicates if there's a previous page
 */
@Serializable
data class PagedResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
) {
    companion object {
        /**
         * Factory method to create a paged response.
         *
         * @param items List of items for the current page
         * @param page Current page number (0-indexed)
         * @param size Number of items per page
         * @param totalElements Total number of items available
         * @return PagedResponse with calculated pagination data
         */
        fun <T> of(
            items: List<T>,
            page: Int,
            size: Int,
            totalElements: Long
        ): PagedResponse<T> {
            val totalPages = if (size > 0) ((totalElements + size - 1) / size).toInt() else 0
            return PagedResponse(
                items = items,
                page = page,
                size = size,
                totalElements = totalElements,
                totalPages = totalPages,
                hasNext = page < totalPages - 1,
                hasPrevious = page > 0
            )
        }
    }
}

