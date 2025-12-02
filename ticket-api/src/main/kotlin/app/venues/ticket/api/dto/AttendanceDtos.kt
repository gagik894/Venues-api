package app.venues.ticket.api.dto

import java.time.Instant
import java.util.*

/**
 * Attendance request describing a target session and its start time.
 */
data class AttendanceRequestDto(
    val sessionId: UUID,
    val startTime: Instant,
    val gracePeriodMinutes: Long = 10
)

/**
 * Aggregated attendance summary returned by the ticket module.
 */
data class AttendanceSummaryDto(
    val sessionId: UUID,
    val soldTickets: Long,
    val presentUsers: Long,
    val presentOnTimeUsers: Long,
    val presentLateUsers: Long,
    val absentUsers: Long
)
