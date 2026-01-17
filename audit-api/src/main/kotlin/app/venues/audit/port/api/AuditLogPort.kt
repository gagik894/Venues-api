package app.venues.audit.port.api

/**
 * @deprecated Use [StaffAuditPort] instead for government-grade staff action auditing.
 * This port writes to the deprecated audit_events table which logged low-value HTTP noise.
 * Will be removed in a future release.
 */
@Deprecated(
    message = "Use StaffAuditPort instead",
    replaceWith = ReplaceWith("StaffAuditPort")
)
interface AuditLogPort {

    /**
     * Writes an audit event to the deprecated audit_events table.
     * @deprecated Use StaffAuditPort.log() instead.
     */
    @Deprecated("Use StaffAuditPort.log() instead")
    fun write(event: AuditEventWriteRequest)
}
