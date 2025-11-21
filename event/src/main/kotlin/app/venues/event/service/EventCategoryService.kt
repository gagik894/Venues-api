package app.venues.event.service

import app.venues.event.api.dto.EventCategoryResponse
import app.venues.event.api.mapper.EventMapper
import app.venues.event.repository.EventCategoryRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Service for event category operations.
 *
 * Handles category listing and retrieval.
 * Categories are managed by administrators.
 */
@Service
@Transactional(readOnly = true)
class EventCategoryService(
    private val categoryRepository: EventCategoryRepository,
    private val eventMapper: EventMapper
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Get all active categories ordered by display order.
     */
    fun getAllCategories(): List<EventCategoryResponse> {
        logger.debug { "Fetching all active event categories" }
        return categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc()
            .map { eventMapper.toCategoryResponse(it) }
    }

    /**
     * Get category by ID.
     */
    fun getCategoryById(id: Long): EventCategoryResponse? {
        logger.debug { "Fetching category by ID: $id" }
        return categoryRepository.findById(id)
            .map { eventMapper.toCategoryResponse(it) }
            .orElse(null)
    }

    /**
     * Get category by key.
     */
    fun getCategoryByCode(code: String): EventCategoryResponse? {
        logger.debug { "Fetching category by code: $code" }
        return categoryRepository.findByCode(code)
            ?.let { eventMapper.toCategoryResponse(it) }
    }
}

