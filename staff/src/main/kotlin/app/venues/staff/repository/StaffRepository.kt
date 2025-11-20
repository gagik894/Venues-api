package app.venues.staff.repository

import app.venues.staff.domain.StaffIdentity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository for Staff entity operations.
 */
@Repository
interface StaffIdentityRepository : JpaRepository<StaffIdentity, UUID> {

    fun findByEmail(email: String): StaffIdentity?
    fun existsByEmail(email: String): Boolean
    fun findByVerificationToken(token: String): StaffIdentity?

    @Query(
        """
        SELECT s FROM StaffIdentity s
        JOIN s.memberships m
        WHERE m.organizationId = :organizationId
        """
    )
    fun findAllByOrganizationId(organizationId: UUID): List<StaffIdentity>
}