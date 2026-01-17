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
 * @deprecated Use [StaffAuditLogEntity] instead. This entity maps to the deprecated
 * audit_events table which has been renamed to audit_events_deprecated in V18 migration.
 * Will be removed in a future release.
 */
@Deprecated(
    message = "Use StaffAuditLogEntity instead",
    replaceWith = ReplaceWith("StaffAuditLogEntity")
)
@Entity
@Table(
    name = "audit_events_deprecated",
    indexes = [
        Index(name = "idx_audit_events_occurred_at", columnList = "occurred_at"),
        Index(name = "idx_audit_events_actor_id", columnList = "actor_id"),
        Index(name = "idx_audit_events_action", columnList = "action"),
        Index(name = "idx_audit_events_subject", columnList = "subject_type,subject_id"),
        Index(name = "idx_audit_events_venue_id", columnList = "venue_id")
    ]
)
class AuditEventEntity(
    @Column(name = "occurred_at", nullable = false, updatable = false)
    var occurredAt: Instant,

    @Column(name = "actor_type", nullable = false, updatable = false, length = 32)
    var actorType: String,

    @Column(name = "actor_id", updatable = false)
    var actorId: UUID?,

    @Column(name = "action", nullable = false, updatable = false, length = 128)
    var action: String,

    @Column(name = "outcome", nullable = false, updatable = false, length = 32)
    var outcome: String,

    @Column(name = "subject_type", updatable = false, length = 128)
    var subjectType: String?,

    @Column(name = "subject_id", updatable = false, length = 128)
    var subjectId: String?,

    @Column(name = "venue_id", updatable = false)
    var venueId: UUID?,

    @Column(name = "organization_id", updatable = false)
    var organizationId: UUID?,

    @Column(name = "http_method", updatable = false, length = 16)
    var httpMethod: String?,

    @Column(name = "http_path", updatable = false, length = 2048)
    var httpPath: String?,

    @Column(name = "http_status", updatable = false)
    var httpStatus: Int?,

    @Column(name = "request_id", updatable = false, length = 128)
    var requestId: String?,

    @Column(name = "correlation_id", updatable = false, length = 128)
    var correlationId: String?,

    @Column(name = "client_ip", updatable = false, length = 64)
    var clientIp: String?,

    @Column(name = "user_agent", updatable = false, length = 512)
    var userAgent: String?,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", updatable = false, columnDefinition = "jsonb")
    var metadataJson: String
) : AbstractUuidEntity()
