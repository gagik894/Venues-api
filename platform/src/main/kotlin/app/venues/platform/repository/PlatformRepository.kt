package app.venues.platform.repository

import app.venues.platform.domain.Platform
import app.venues.platform.domain.PlatformStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository for Platform entity operations.
 */
@Repository
interface PlatformRepository : JpaRepository<Platform, UUID> {

    /**
     * Find platform by name
     */
    fun findByName(name: String): Platform?

    /**
     * Find platforms by status
     */
    fun findByStatus(status: PlatformStatus, pageable: Pageable): Page<Platform>

    /**
     * Find all active platforms with webhooks enabled
     */
    fun findByStatusAndWebhookEnabled(
        status: PlatformStatus,
        webhookEnabled: Boolean
    ): List<Platform>

    /**
     * Check if platform exists by name
     */
    fun existsByName(name: String): Boolean
}

