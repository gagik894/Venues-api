package app.venues.common.model

import app.venues.common.constants.AppConstants

/**
 * Framework-agnostic pagination request parameters.
 *
 * This data class represents pagination and sorting requirements without
 * coupling to any specific ORM or data access framework. It can be converted
 * to framework-specific types (e.g., Spring Data Pageable) by infrastructure layers.
 *
 * Design Rationale:
 * By keeping pagination logic in the common module as pure DTOs, we enable
 * consistent pagination semantics across all modules while maintaining
 * flexibility to integrate with different data access technologies.
 *
 * Validation Rules:
 * - Limit is coerced to [1, 100] range
 * - Offset is coerced to minimum of 0
 * - SortBy must be validated against allowed fields by calling code
 *
 * @property limit Maximum number of items per page (defaults to 20)
 * @property offset Zero-based page number (defaults to 0)
 * @property sortBy Field name to sort by (nullable - defaults handled by caller)
 * @property sortDirection Sort direction (ASC/DESC, nullable - defaults handled by caller)
 */
data class PaginationRequest(
    val limit: Int = AppConstants.Pagination.DEFAULT_SIZE,
    val offset: Int = AppConstants.Pagination.DEFAULT_PAGE,
    val sortBy: String? = null,
    val sortDirection: String? = null
) {
    /**
     * Returns validated page size within allowed range.
     *
     * @return Page size coerced to [MIN_SIZE, MAX_SIZE]
     */
    fun validatedLimit(): Int = limit.coerceIn(
        AppConstants.Pagination.MIN_SIZE,
        AppConstants.Pagination.MAX_SIZE
    )

    /**
     * Returns validated page offset (non-negative).
     *
     * @return Page offset coerced to minimum of 0
     */
    fun validatedOffset(): Int = offset.coerceAtLeast(0)

    /**
     * Returns validated sort direction enum.
     *
     * @return Parsed sort direction, defaults to DESC if invalid
     */
    fun validatedDirection(): SortDirection = try {
        SortDirection.valueOf(sortDirection?.uppercase() ?: "DESC")
    } catch (_: IllegalArgumentException) {
        SortDirection.DESC
    }

    /**
     * Validates sort field against allowed fields whitelist.
     *
     * @param allowedFields Set of permitted field names
     * @return Validated field name or null if invalid/not provided
     */
    fun validatedSortBy(allowedFields: Set<String>): String? {
        return sortBy
            ?.trim()
            ?.takeIf { it.isNotBlank() && it in allowedFields }
    }
}

/**
 * Sort direction enumeration.
 *
 * Framework-agnostic representation of sort order.
 */
enum class SortDirection {
    /** Ascending order (A-Z, 0-9, oldest first) */
    ASC,

    /** Descending order (Z-A, 9-0, newest first) */
    DESC
}

/**
 * Sort field specification.
 *
 * Represents a single sort criterion with field name and direction.
 * Can be used to build multi-field sorting specifications.
 *
 * @property field Field name to sort by
 * @property direction Sort direction (ASC/DESC)
 */
data class SortField(
    val field: String,
    val direction: SortDirection = SortDirection.DESC
)

/**
 * Pagination response metadata.
 *
 * Contains pagination information to be included in API responses,
 * allowing clients to navigate through paginated results.
 *
 * @property currentPage Current page number (0-based)
 * @property pageSize Number of items per page
 * @property totalItems Total number of items across all pages
 * @property totalPages Total number of pages
 * @property hasNext Whether there is a next page
 * @property hasPrevious Whether there is a previous page
 */
data class PageMetadata(
    val currentPage: Int,
    val pageSize: Int,
    val totalItems: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
) {
    companion object {
        /**
         * Creates pagination metadata from raw values.
         *
         * @param currentPage Current page number (0-based)
         * @param pageSize Number of items per page
         * @param totalItems Total number of items
         * @return Calculated page metadata
         */
        fun from(currentPage: Int, pageSize: Int, totalItems: Long): PageMetadata {
            val totalPages = if (pageSize > 0) {
                ((totalItems + pageSize - 1) / pageSize).toInt()
            } else {
                0
            }

            return PageMetadata(
                currentPage = currentPage,
                pageSize = pageSize,
                totalItems = totalItems,
                totalPages = totalPages,
                hasNext = currentPage < totalPages - 1,
                hasPrevious = currentPage > 0
            )
        }
    }
}

