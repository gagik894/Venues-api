package app.venues.staff.repository

import app.venues.staff.domain.StaffVenuePermission
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface StaffVenuePermissionRepository : JpaRepository<StaffVenuePermission, UUID> {

    @EntityGraph(attributePaths = ["membership", "membership.staff"])
    @Query(
        """
        SELECT vp FROM StaffVenuePermission vp
        JOIN vp.membership m
        JOIN m.staff s
        WHERE vp.venueId = :venueId
        """
    )
    fun findByVenueId(
        venueId: UUID,
        pageable: org.springframework.data.domain.Pageable
    ): org.springframework.data.domain.Page<StaffVenuePermission>
}

