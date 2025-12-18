package app.venues.audit.port.api

/**
 * Port for writing audit events.
 *
 * Implementations must be append-only and durable.
 */
interface AuditLogPort {

    /**
     * Writes an audit event.
     *
     * Implementations should avoid throwing where possible; if a failure occurs,
     * it must not leak secrets.
     */
    fun write(event: AuditEventWriteRequest)
}
