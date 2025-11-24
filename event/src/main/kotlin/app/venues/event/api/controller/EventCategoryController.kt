package app.venues.event.api.controller

import app.venues.common.model.ApiResponse
import app.venues.event.api.dto.EventCategoryResponse
import app.venues.event.service.EventCategoryService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Controller for event category operations.
 *
 * Provides endpoints for browsing event categories.
 */
@RestController
@RequestMapping("/api/v1/events/categories")
@Tag(name = "Event Categories", description = "Event category browsing")
class EventCategoryController(
    private val categoryService: EventCategoryService
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Get all active categories.
     */
    @GetMapping
    @Operation(
        summary = "Get all categories",
        description = "Get all active event categories ordered by display order"
    )
    fun getAllCategories(): ApiResponse<List<EventCategoryResponse>> {
        logger.debug { "Fetching all event categories" }

        val categories = categoryService.getAllCategories()

        return ApiResponse.success(
            data = categories,
            message = "Categories retrieved successfully"
        )
    }

    /**
     * Get category by ID.
     */
    @GetMapping("/{id}")
    @Operation(
        summary = "Get category by ID",
        description = "Get details of a specific category"
    )
    fun getCategoryById(@PathVariable id: Long): ApiResponse<EventCategoryResponse?> {
        logger.debug { "Fetching category: $id" }

        val category = categoryService.getCategoryById(id)

        return ApiResponse.success(
            data = category,
            message = "Category retrieved successfully"
        )
    }

    /**
     * Get category by key.
     */
    @GetMapping("/key/{key}")
    @Operation(
        summary = "Get category by key",
        description = "Get category by unique key (e.g., 'THEATER', 'CONCERT')"
    )
    fun getCategoryByKey(@PathVariable key: String): ApiResponse<EventCategoryResponse?> {
        logger.debug { "Fetching category by key: $key" }

        val category = categoryService.getCategoryByCode(key.uppercase())

        return ApiResponse.success(
            data = category,
            message = "Category retrieved successfully"
        )
    }
}

