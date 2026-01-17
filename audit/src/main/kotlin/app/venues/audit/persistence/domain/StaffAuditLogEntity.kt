package app.venues.audit.persistence.domain

import app.venues.shared.persistence.domain.AbstractUuidEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.*

/**
 * Entity for government-grade staff audit log.
 * Tracks all meaningful staff actions with full accountability.
 */
@Entity
@Table(
    name = "staff_audit_log",
    indexes = [
        Index(name = "idx_staff_audit_staff_time", columnList = "staff_id, occurred_at DESC"),
        Index(name = "idx_staff_audit_venue_time", columnList = "venue_id, occurred_at DESC"),
        Index(name = "idx_staff_audit_action", columnList = "action"),
        Index(name = "idx_staff_audit_category_time", columnList = "category, occurred_at DESC")
    ]
)
class StaffAuditLogEntity(
    @Column(name = "occurred_at", nullable = false, updatable = false)
    var occurredAt: Instant,

    @Column(name = "staff_id", nullable = false, updatable = false)
    var staffId: UUID,

    @Column(name = "platform_id", updatable = false)
    var platformId: UUID? = null,

    @Column(name = "venue_id", updatable = false)
    var venueId: UUID? = null,

    @Column(name = "organization_id", updatable = false)
    var organizationId: UUID? = null,

    @Column(name = "action", nullable = false, updatable = false, length = 64)
    var action: String,

    @Column(name = "category", nullable = false, updatable = false, length = 32)
    var category: String,

    @Column(name = "severity", nullable = false, updatable = false, length = 16)
    var severity: String,

    @Column(name = "subject_type", updatable = false, length = 64)
    var subjectType: String? = null,

    @Column(name = "subject_id", updatable = false, length = 128)
    var subjectId: String? = null,

    @Column(name = "description", updatable = false, columnDefinition = "TEXT")
    var description: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changes", updatable = false, columnDefinition = "jsonb")
    var changesJson: String? = null,

    @Column(name = "outcome", nullable = false, updatable = false, length = 16)
    var outcome: String,

    @Column(name = "failure_reason", updatable = false, columnDefinition = "TEXT")
    var failureReason: String? = null,

    @Column(name = "client_ip", updatable = false, length = 64)
    var clientIp: String? = null,

    @Column(name = "user_agent", updatable = false, length = 512)
    var userAgent: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, updatable = false, columnDefinition = "jsonb")
    var metadataJson: String = "{}"
) : AbstractUuidEntity()
