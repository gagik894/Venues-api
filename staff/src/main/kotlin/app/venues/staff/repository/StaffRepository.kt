package app.venues.staff.repository

import app.venues.organization.domain.Organization
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
     * @param organization Organization entity
     * @param pageable Pagination parameters
     * @return Page of staff members
     */
    fun findByOrganization(organization: Organization, pageable: Pageable): Page<Staff>

    /**
     * Finds all active staff in an organization.
     *
     * @param organization Organization entity
     * @param status Staff status filter
     * @param pageable Pagination parameters
     * @return Page of staff members
     */
    fun findByOrganizationAndStatus(
        organization: Organization,
        status: StaffStatus,
        pageable: Pageable
    ): Page<Staff>

    /**
     * Counts staff members by organization and status.
     *
     * @param organization Organization entity
     * @param status Staff status filter
     * @return Count of staff members
     */
    fun countByOrganizationAndStatus(organization: Organization, status: StaffStatus): Long

    /**
     * Finds staff awaiting email verification.
     *
     * @return List of unverified staff
     */
    @Query("SELECT s FROM Staff s WHERE s.status = 'PENDING_EMAIL_VERIFICATION' AND s.emailVerified = false")
    fun findUnverifiedStaff(): List<Staff>

    /**
     * Finds staff by verification token.
     *
     * @param token Verification token
     * @return Staff if found
     */
    fun findByVerificationToken(token: String): Staff?
}

