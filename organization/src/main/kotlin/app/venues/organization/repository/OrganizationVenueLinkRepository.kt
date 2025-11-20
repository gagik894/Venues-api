package app.venues.organization.repository

import app.venues.organization.domain.OrganizationVenueLink
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository for OrganizationVenueLink entity operations.
 */
@Repository
interface OrganizationVenueLinkRepository : JpaRepository<OrganizationVenueLink, Long> {

    /**
     * Finds venue links for an organization.
     *
     * @param organizationId Organization UUID
     * @param isActive Filter by active status
     * @return List of venue links
     */
    fun findByOrganizationIdAndIsActive(organizationId: UUID, isActive: Boolean = true): List<OrganizationVenueLink>

    /**
     * Checks if venue is linked to organization.
     *
     * @param organizationId Organization UUID
     * @param venueId Venue UUID
     * @return true if linked
     */
    fun existsByOrganizationIdAndVenueId(organizationId: UUID, venueId: UUID): Boolean

    /**
     * Finds link for specific organization and venue.
     *
     * @param organizationId Organization UUID
     * @param venueId Venue UUID
     * @return OrganizationVenueLink if found
     */
    fun findByOrganizationIdAndVenueId(organizationId: UUID, venueId: UUID): OrganizationVenueLink?
}

