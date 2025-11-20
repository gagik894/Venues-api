package app.venues.staff.domain

import app.venues.shared.persistence.domain.AbstractUuidEntity
import jakarta.persistence.*
import java.util.*

/**
 * Represents the employment link: "StaffIdentity X works at Organization Y".
 */
@Entity
@Table(
    name = "staff_memberships",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["staff_identity_id", "organization_id"])
    ]
)
class StaffMembership(

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_identity_id", nullable = false)
    var staff: StaffIdentity,

    @Column(name = "organization_id", nullable = false)
    var organizationId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "org_role", nullable = false)
    var orgRole: OrganizationRole = OrganizationRole.MEMBER,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

) : AbstractUuidEntity() {

    @OneToMany(mappedBy = "membership", cascade = [CascadeType.ALL], orphanRemoval = true)
    var venuePermissions: MutableSet<StaffVenuePermission> = mutableSetOf()
}