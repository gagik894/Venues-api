package app.venues.event.repository

import app.venues.event.domain.EventCategory
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Repository for EventCategory entity operations.
 */
@Repository
interface EventCategoryRepository : JpaRepository<EventCategory, Long> {

    /**
     * Find category by unique key
     */
    fun findByCategoryKey(categoryKey: String): EventCategory?

    /**
     * Find all active categories ordered by display order
     */
    fun findByIsActiveTrueOrderByDisplayOrderAsc(): List<EventCategory>

    /**
     * Find all categories with sorting
     */
    fun findByIsActiveTrue(sort: Sort): List<EventCategory>

    /**
     * Check if category key exists
     */
    fun existsByCategoryKey(categoryKey: String): Boolean
}

