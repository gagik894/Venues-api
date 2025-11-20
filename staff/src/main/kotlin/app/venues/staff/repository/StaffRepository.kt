package app.venues.staff.repository

import app.venues.staff.domain.Staff
import app.venues.staff.domain.StaffStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository for Staff entity operations.
 */
@Repository
interface StaffRepository : JpaRepository<Staff, UUID> {

    /**
     * Finds staff by email (login lookup).
     *
     * @param email Staff email
     * @return Staff if found
     */
    fun findByEmail(email: String): Staff?

    /**
     * Checks if email exists.
     *
     * @param email Staff email
     * @return true if exists
     */
    fun existsByEmail(email: String): Boolean

    /**
     * Finds all staff in an organization.
     *
     * @param organizationId Organization UUID
     * @param pageable Pagination parameters
     * @return Page of staff members
     */
    fun findByOrganizationId(organizationId: UUID, pageable: Pageable): Page<Staff>

    /**
     * Finds all active staff in an organization.
     *
     * @param organizationId Organization UUID
     * @param status Staff status filter
     * @param pageable Pagination parameters
     * @return Page of staff members
     */
    fun findByOrganizationIdAndStatus(
        organizationId: UUID,
        status: StaffStatus,
        pageable: Pageable
    ): Page<Staff>

    /**
     * Counts staff members by organization and status.
     *
     * @param organizationId Organization UUID
     * @param status Staff status filter
     * @return Count of staff members
     */
    fun countByOrganizationIdAndStatus(organizationId: UUID, status: StaffStatus): Long

    /**
     * Finds staff awaiting email verification.
     *
     * @return List of unverified staff
     */
    @Query("SELECT s FROM Staff s WHERE s.status = 'PENDING_EMAIL_VERIFICATION'")
    fun findUnverifiedStaff(): List<Staff>

    /**
     * Finds staff by verification token.
     *
     * @param token Verification token
     * @return Staff if found
     */
    fun findByVerificationToken(token: String): Staff?
}

