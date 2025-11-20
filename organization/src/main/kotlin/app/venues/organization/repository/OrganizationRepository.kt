package app.venues.organization.repository

import app.venues.organization.domain.Organization
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository for Organization entity operations.
 */
@Repository
interface OrganizationRepository : JpaRepository<Organization, UUID> {

    /**
     * Finds organization by slug.
     *
     * @param slug Organization slug
     * @return Organization if found
     */
    fun findBySlug(slug: String): Organization?

    /**
     * Checks if slug exists.
     *
     * @param slug Organization slug
     * @return true if exists
     */
    fun existsBySlug(slug: String): Boolean

    /**
     * Finds all active organizations.
     *
     * @param pageable Pagination parameters
     * @return Page of organizations
     */
    fun findByIsActive(isActive: Boolean, pageable: Pageable): Page<Organization>

    /**
     * Finds all organizations (including inactive).
     *
     * @param pageable Pagination parameters
     * @return Page of organizations
     */
    @Query("SELECT o FROM Organization o WHERE o.isActive = true ORDER BY o.name ASC")
    fun findAllActive(pageable: Pageable): Page<Organization>
}

