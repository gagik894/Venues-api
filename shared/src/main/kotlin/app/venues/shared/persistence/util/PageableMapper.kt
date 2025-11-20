/*
 * Venues API - Government-Sponsored Cultural Venues Portal
 * Copyright (c) 2025 Government Cultural Department
 *
 * Bridge utility for converting framework-agnostic pagination models to Spring Data types.
 */

package app.venues.shared.persistence.util

import app.venues.common.constants.AppConstants
import app.venues.common.model.PageMetadata
import app.venues.common.model.PaginationRequest
import app.venues.common.model.SortDirection
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

/**
 * Utility for converting between common pagination DTOs and Spring Data Pageable.
 *
 * This utility serves as the integration point between our framework-agnostic
 * pagination models (in common module) and Spring Data's Pageable interface.
 * It ensures consistent pagination behavior across all Spring Data repositories.
 *
 * Responsibilities:
 * - Convert PaginationRequest to Spring Data Pageable
 * - Convert Spring Data Page to framework-agnostic PageMetadata
 * - Apply validation and security checks on sort fields
 * - Enforce pagination limits and defaults
 *
 * Security Considerations:
 * Always validate sortBy fields against a whitelist to prevent SQL injection
 * through malicious field names.
 */
object PageableMapper {

    /**
     * Converts PaginationRequest to Spring Data Pageable with sorting.
     *
     * This method applies validation, coercion, and security checks before
     * creating the Spring Data Pageable object. Sort fields are validated
     * against a whitelist to prevent injection attacks.
     *
     * @param request Framework-agnostic pagination request
     * @param allowedSortFields Whitelist of permitted field names for sorting
     * @param defaultSortField Default field to sort by if none provided or invalid
     * @return Spring Data Pageable with validated parameters
     * @throws IllegalArgumentException if allowedSortFields is empty
     */
    fun toPageable(
        request: PaginationRequest,
        allowedSortFields: Set<String> = setOf("createdAt", "id"),
        defaultSortField: String = "createdAt"
    ): Pageable {
        require(allowedSortFields.isNotEmpty()) {
            "Allowed sort fields cannot be empty"
        }
        require(defaultSortField in allowedSortFields) {
            "Default sort field must be in allowed fields"
        }

        val pageSize = request.validatedLimit()
        val pageNumber = request.validatedOffset()

        // Validate and sanitize sort field against whitelist
        val sortField = request.validatedSortBy(allowedSortFields) ?: defaultSortField

        // Convert direction to Spring Data enum
        val direction = when (request.validatedDirection()) {
            SortDirection.ASC -> Sort.Direction.ASC
            SortDirection.DESC -> Sort.Direction.DESC
        }

        val sort = Sort.by(direction, sortField)
        return PageRequest.of(pageNumber, pageSize, sort)
    }

    /**
     * Converts PaginationRequest to Spring Data Pageable without sorting.
     *
     * Use this method when sorting is not required or handled separately.
     *
     * @param request Framework-agnostic pagination request
     * @return Spring Data Pageable with validated parameters (unsorted)
     */
    fun toPageableUnsorted(request: PaginationRequest): Pageable {
        val pageSize = request.validatedLimit()
        val pageNumber = request.validatedOffset()
        return PageRequest.of(pageNumber, pageSize)
    }

    /**
     * Creates Pageable with optional sorting from raw parameters.
     *
     * Convenience method for controller endpoints that receive pagination
     * parameters as separate query params.
     *
     * @param limit Page size (will be coerced to valid range)
     * @param offset Page number (0-based)
     * @param sortBy Sort field (must be in allowed fields)
     * @param sortDirection Sort direction (ASC/DESC, defaults to DESC)
     * @param allowedSortFields Whitelist of fields that can be sorted on
     * @param defaultSortField Default field if sortBy is invalid or null
     * @return Validated Spring Data Pageable object
     */
    fun createPageable(
        limit: Int?,
        offset: Int?,
        sortBy: String?,
        sortDirection: String?,
        allowedSortFields: Set<String> = setOf("createdAt", "id"),
        defaultSortField: String = "createdAt"
    ): Pageable {
        val request = PaginationRequest(
            limit = limit ?: AppConstants.Pagination.DEFAULT_SIZE,
            offset = offset ?: AppConstants.Pagination.DEFAULT_PAGE,
            sortBy = sortBy,
            sortDirection = sortDirection
        )
        return toPageable(request, allowedSortFields, defaultSortField)
    }

    /**
     * Creates Pageable without sorting from raw parameters.
     *
     * @param limit Page size (will be coerced to valid range)
     * @param offset Page number (0-based)
     * @return Validated Spring Data Pageable object (unsorted)
     */
    fun createPageableUnsorted(
        limit: Int?,
        offset: Int?
    ): Pageable {
        val request = PaginationRequest(
            limit = limit ?: AppConstants.Pagination.DEFAULT_SIZE,
            offset = offset ?: AppConstants.Pagination.DEFAULT_PAGE
        )
        return toPageableUnsorted(request)
    }

    /**
     * Extracts framework-agnostic pagination metadata from Spring Data Page.
     *
     * This method converts Spring Data Page information into our common
     * PageMetadata DTO, which can be serialized in API responses without
     * exposing Spring Data types to clients.
     *
     * @param page Spring Data Page object
     * @return Framework-agnostic page metadata
     */
    fun <T> toPageMetadata(page: Page<T>): PageMetadata {
        return PageMetadata(
            currentPage = page.number,
            pageSize = page.size,
            totalItems = page.totalElements,
            totalPages = page.totalPages,
            hasNext = page.hasNext(),
            hasPrevious = page.hasPrevious()
        )
    }
}

