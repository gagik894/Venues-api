package app.venues.ticket.api

import app.venues.ticket.api.dto.AttendanceRequestDto
import app.venues.ticket.api.dto.AttendanceSummaryDto

/**
 * Port interface for retrieving ticket attendance metrics (per session).
 *
 * This API lets downstream modules (booking, analytics) fetch real-time
 * attendance data without touching ticket repositories directly.
 */
interface TicketAttendanceApi {
    /**
     * Returns attendance summaries for the requested sessions.
     *
     * @param requests Session/time inputs (includes grace period minutes).
     */
    fun getAttendanceSummaries(requests: List<AttendanceRequestDto>): List<AttendanceSummaryDto>
}
