package app.venues.ticket.service

import app.venues.ticket.api.ScannerSessionApi
import app.venues.ticket.api.dto.ScannerSessionDto
import app.venues.ticket.api.mapper.ScannerSessionMapper
import app.venues.ticket.domain.ScannerSession
import app.venues.ticket.repository.ScannerSessionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@Service
class ScannerSessionService(
    private val sessionRepository: ScannerSessionRepository,
    private val scannerSessionMapper: ScannerSessionMapper
) : ScannerSessionApi {

    @Transactional
    override fun createSession(
        eventId: UUID,
        sessionName: String,
        validUntil: Instant,
        scanLocation: String?,
        venueId: UUID,
        staffId: UUID
    ): ScannerSessionDto {

        // Generate secure random token
        val secretToken = UUID.randomUUID().toString().replace("-", "") +
                UUID.randomUUID().toString().replace("-", "")

        val session = ScannerSession(
            eventId = eventId,
            sessionName = sessionName,
            secretToken = secretToken,
            validUntil = validUntil,
            scanLocation = scanLocation,
            venueId = venueId,
            createdByStaffId = staffId
        )

        val savedSession = sessionRepository.save(session)

        // QR Content: SCAN:{eventId}:{secretToken}
        val qrContent = "SCAN:$eventId:$secretToken"

        return scannerSessionMapper.toDto(savedSession, qrContent)
    }

    @Transactional(readOnly = true)
    override fun validateSession(token: String): ScannerSessionDto? {
        val session = sessionRepository.findBySecretToken(token) ?: return null

        if (!session.isValid()) {
            return null
        }

        return scannerSessionMapper.toDto(session, "HIDDEN")
    }

}
