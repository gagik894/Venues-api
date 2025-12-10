package app.venues.staff.repository

import app.venues.staff.domain.StaffIdentity
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository for Staff entity operations.
 *
 * Performance Optimizations:
 * - Uses @EntityGraph to prevent N+1 queries when loading staff with memberships
 * - Email queries use LOWER() for case-insensitive matching
 */
@Repository
interface StaffIdentityRepository : JpaRepository<StaffIdentity, UUID> {

    /**
     * Finds staff by email (case-insensitive).
     * Uses LOWER() function to ensure case-insensitive matching across all databases.
     */
    @EntityGraph(attributePaths = ["memberships", "memberships.venuePermissions"])
    @Query("SELECT s FROM StaffIdentity s WHERE LOWER(s.email) = LOWER(:email)")
    fun findByEmail(email: String): StaffIdentity?

    /**
     * Checks if staff exists by email (case-insensitive).
     */
    @Query("SELECT COUNT(s) > 0 FROM StaffIdentity s WHERE LOWER(s.email) = LOWER(:email)")
    fun existsByEmail(email: String): Boolean

    /**
     * Finds staff by verification token.
     */
    fun findByVerificationToken(token: String): StaffIdentity?

    /**
     * Finds staff by ID with eagerly loaded memberships and venue permissions.
     * Prevents N+1 query problem when building staff context.
     *
     * Without this: 1 query for staff + N queries for memberships + M queries for venue permissions
     * With this: Single JOIN query fetching everything
     */
    @EntityGraph(attributePaths = ["memberships", "memberships.venuePermissions"])
    override fun findById(id: UUID): Optional<StaffIdentity>

    /**
     * Finds all staff in an organization.
     */
    @Query(
        """
        SELECT s FROM StaffIdentity s
        JOIN s.memberships m
        WHERE m.organizationId = :organizationId
        """
    )
    fun findAllByOrganizationId(
        organizationId: UUID,
        pageable: org.springframework.data.domain.Pageable
    ): org.springframework.data.domain.Page<StaffIdentity>

    /**
     * Finds all staff that have a permission for a specific venue.
     */
    @EntityGraph(attributePaths = ["memberships", "memberships.venuePermissions"])
    @Query(
        """
        SELECT s FROM StaffIdentity s
        JOIN s.memberships m
        JOIN m.venuePermissions vp
        WHERE vp.venueId = :venueId
        """
    )
    fun findAllByVenueId(
        venueId: UUID,
        pageable: org.springframework.data.domain.Pageable
    ): org.springframework.data.domain.Page<StaffIdentity>
}
