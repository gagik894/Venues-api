package app.venues.audit.port.api

import app.venues.audit.model.StaffAuditEntry

/**
 * Port interface for writing staff audit log entries.
 * Implementations must ensure audit writes succeed even if the parent transaction fails.
 */
interface StaffAuditPort {

    /**
     * Log a staff audit entry.
     * This method is fire-and-forget: failures are logged but do not propagate.
     */
    fun log(entry: StaffAuditEntry)

    /**
     * Log multiple audit entries atomically.
     */
    fun logAll(entries: List<StaffAuditEntry>)
}
