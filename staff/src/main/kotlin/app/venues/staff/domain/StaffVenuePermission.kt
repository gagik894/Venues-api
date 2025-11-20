package app.venues.staff.domain

import app.venues.shared.persistence.domain.AbstractUuidEntity
import jakarta.persistence.*
import java.util.*

/**
 * Granular permission: "This membership allows managing Venue Z".
 */
@Entity
@Table(
    name = "staff_venue_permissions",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["membership_id", "venue_id"])
    ]
)
class StaffVenuePermission(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_id", nullable = false)
    var membership: StaffMembership,

    @Column(name = "venue_id", nullable = false)
    var venueId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    var role: VenueRole

) : AbstractUuidEntity()