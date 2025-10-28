package app.venues.common.util

import app.venues.common.constants.AppConstants
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

/**
 * Utility for creating pageable requests with validation.
 *
 * Provides consistent pagination handling across all controllers.
 * Validates parameters and applies sensible defaults.
 */
object PaginationUtil {

    /**
     * Create a Pageable with optional sorting.
     *
     * @param limit Page size (will be coerced to valid range)
     * @param offset Page number (0-based)
     * @param sortBy Sort field (must be in allowed fields, defaults to createdAt)
     * @param sortDirection Sort direction (ASC/DESC, defaults to DESC)
     * @param allowedSortFields Whitelist of fields that can be sorted on
     * @return Validated Pageable object
     */
    fun createPageable(
        limit: Int?,
        offset: Int?,
        sortBy: String?,
        sortDirection: String?,
        allowedSortFields: Set<String> = setOf("createdAt", "id")
    ): Pageable {
        val pageSize = (limit ?: AppConstants.Pagination.DEFAULT_SIZE)
            .coerceIn(AppConstants.Pagination.MIN_SIZE, AppConstants.Pagination.MAX_SIZE)

        val pageNumber = (offset ?: AppConstants.Pagination.DEFAULT_PAGE)
            .coerceAtLeast(0)

        // Validate and sanitize sort field
        val sortField = sortBy
            ?.trim()
            ?.takeIf { it.isNotBlank() && it in allowedSortFields }
            ?: "createdAt"

        // Parse sort direction safely
        val direction = parseSortDirection(sortDirection)

        val sort = Sort.by(direction, sortField)
        return PageRequest.of(pageNumber, pageSize, sort)
    }

    /**
     * Create a Pageable without sorting.
     *
     * @param limit Page size (will be coerced to valid range)
     * @param offset Page number (0-based)
     * @return Validated Pageable object
     */
    fun createPageable(
        limit: Int?,
        offset: Int?
    ): Pageable {
        val pageSize = (limit ?: AppConstants.Pagination.DEFAULT_SIZE)
            .coerceIn(AppConstants.Pagination.MIN_SIZE, AppConstants.Pagination.MAX_SIZE)

        val pageNumber = (offset ?: AppConstants.Pagination.DEFAULT_PAGE)
            .coerceAtLeast(0)

        return PageRequest.of(pageNumber, pageSize)
    }

    /**
     * Safely parse sort direction with fallback to DESC.
     *
     * @param sortDirection String representation (case-insensitive)
     * @return Sort.Direction (defaults to DESC on error)
     */
    private fun parseSortDirection(sortDirection: String?): Sort.Direction {
        return try {
            Sort.Direction.fromString(sortDirection?.uppercase() ?: "DESC")
        } catch (_: IllegalArgumentException) {
            Sort.Direction.DESC
        }
    }
}
