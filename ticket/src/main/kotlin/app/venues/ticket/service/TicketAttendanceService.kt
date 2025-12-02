package app.venues.ticket.service

import app.venues.ticket.api.TicketAttendanceApi
import app.venues.ticket.api.dto.AttendanceRequestDto
import app.venues.ticket.api.dto.AttendanceSummaryDto
import app.venues.ticket.repository.TicketFirstScanProjection
import app.venues.ticket.repository.TicketRepository
import app.venues.ticket.repository.TicketScanRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class TicketAttendanceService(
    private val ticketRepository: TicketRepository,
    private val ticketScanRepository: TicketScanRepository
) : TicketAttendanceApi {

    private val logger = KotlinLogging.logger {}

    @Transactional(readOnly = true)
    override fun getAttendanceSummaries(requests: List<AttendanceRequestDto>): List<AttendanceSummaryDto> {
        if (requests.isEmpty()) {
            logger.debug { "Attendance summary requested with empty session list" }
            return emptyList()
        }

        return requests.map { request ->
            val soldTickets = ticketRepository.countByEventSessionId(request.sessionId)
            if (soldTickets == 0L) {
                return@map AttendanceSummaryDto(
                    sessionId = request.sessionId,
                    soldTickets = 0,
                    presentUsers = 0,
                    presentOnTimeUsers = 0,
                    presentLateUsers = 0,
                    absentUsers = 0
                )
            }

            val firstScanRows = ticketScanRepository.findFirstScansBySessionId(request.sessionId)
            val presentUsers = firstScanRows.size.toLong()
            val cutoff = request.startTime.plusSeconds(request.gracePeriodMinutes * 60)
            val (onTime, late) = classifyAttendance(firstScanRows, cutoff)
            val absent = (soldTickets - presentUsers).coerceAtLeast(0)

            AttendanceSummaryDto(
                sessionId = request.sessionId,
                soldTickets = soldTickets,
                presentUsers = presentUsers,
                presentOnTimeUsers = onTime,
                presentLateUsers = late,
                absentUsers = absent
            )
        }
    }

    private fun classifyAttendance(
        rows: List<TicketFirstScanProjection>,
        cutoff: Instant
    ): Pair<Long, Long> {
        var onTime = 0L
        var late = 0L
        rows.forEach { projection ->
            val firstScan = projection.getFirstScan()
            if (firstScan != null && firstScan <= cutoff) {
                onTime++
            } else {
                late++
            }
        }
        return onTime to late
    }
}
