package app.venues.staff.domain

import app.venues.shared.persistence.domain.AbstractUuidEntity
import jakarta.persistence.*
import java.util.*

/**
 * Granular permissions table.
 * Connects a Staff member to a specific Venue (by ID) with a specific Role.
 */
@Entity
@Table(
    name = "staff_venue_scopes",
    indexes = [
        Index(name = "idx_scope_staff", columnList = "staff_id"),
        Index(name = "idx_scope_venue", columnList = "venue_id")
    ],
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["staff_id", "venue_id"])
    ]
)
class StaffVenueScope(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    var staff: Staff,

    @Column(name = "venue_id", nullable = false)
    var venueId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    var role: StaffVenueRole

) : AbstractUuidEntity()