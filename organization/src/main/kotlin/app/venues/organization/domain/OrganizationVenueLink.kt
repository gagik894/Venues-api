package app.venues.organization.domain

import app.venues.shared.persistence.domain.AbstractLongEntity
import jakarta.persistence.*
import java.util.*

/**
 * Junction entity linking Organization and Venue with relationship metadata.
 *
 * Enables:
 * - An organization to manage multiple venues
 * - Tracking of when venues were added to organizations
 * - Soft deletion without losing history
 */
@Entity
@Table(
    name = "organization_venue_links",
    indexes = [
        Index(name = "idx_org_venue_link_org", columnList = "organization_id"),
        Index(name = "idx_org_venue_link_venue", columnList = "venue_id"),
        Index(name = "idx_org_venue_link_active", columnList = "is_active")
    ]
)
class OrganizationVenueLink(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    var organization: Organization,

    @JoinColumn(name = "venue_id", nullable = false)
    var venueId: UUID,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

) : AbstractLongEntity()

