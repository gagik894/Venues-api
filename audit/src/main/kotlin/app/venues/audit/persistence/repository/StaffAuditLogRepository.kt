package app.venues.audit.persistence.repository

import app.venues.audit.persistence.domain.StaffAuditLogEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.*

/**
 * Repository for staff audit log queries.
 * Provides methods for compliance reporting and activity review.
 */
@Repository
interface StaffAuditLogRepository : JpaRepository<StaffAuditLogEntity, UUID> {

    /**
     * Find all audit entries for a specific staff member.
     */
    fun findByStaffIdOrderByOccurredAtDesc(staffId: UUID, pageable: Pageable): Page<StaffAuditLogEntity>

    /**
     * Find all audit entries for a specific venue within a time range.
     */
    fun findByVenueIdAndOccurredAtBetweenOrderByOccurredAtDesc(
        venueId: UUID,
        startTime: Instant,
        endTime: Instant,
        pageable: Pageable
    ): Page<StaffAuditLogEntity>

    /**
     * Find all audit entries for a specific action type.
     */
    fun findByActionOrderByOccurredAtDesc(action: String, pageable: Pageable): Page<StaffAuditLogEntity>

    /**
     * Find all audit entries in a category within a time range.
     */
    fun findByCategoryAndOccurredAtBetweenOrderByOccurredAtDesc(
        category: String,
        startTime: Instant,
        endTime: Instant,
        pageable: Pageable
    ): Page<StaffAuditLogEntity>

    /**
     * Find failed actions for troubleshooting.
     */
    fun findByOutcomeOrderByOccurredAtDesc(outcome: String, pageable: Pageable): Page<StaffAuditLogEntity>

    /**
     * Find critical security events within a time range.
     */
    @Query(
        """
        SELECT e FROM StaffAuditLogEntity e 
        WHERE e.category = 'SECURITY' 
        AND e.severity = 'CRITICAL' 
        AND e.occurredAt BETWEEN :startTime AND :endTime 
        ORDER BY e.occurredAt DESC
    """
    )
    fun findCriticalSecurityEvents(
        startTime: Instant,
        endTime: Instant,
        pageable: Pageable
    ): Page<StaffAuditLogEntity>

    /**
     * Find all actions on a specific subject (e.g., a specific event).
     */
    fun findBySubjectTypeAndSubjectIdOrderByOccurredAtDesc(
        subjectType: String,
        subjectId: String,
        pageable: Pageable
    ): Page<StaffAuditLogEntity>

    /**
     * Count entries older than a given date (for retention policy monitoring).
     */
    fun countByOccurredAtBefore(cutoff: Instant): Long
}
