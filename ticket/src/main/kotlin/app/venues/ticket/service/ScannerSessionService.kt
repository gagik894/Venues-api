package app.venues.ticket.service

import app.venues.ticket.api.ScannerSessionApi
import app.venues.ticket.api.dto.ScannerSessionDto
import app.venues.ticket.domain.ScannerSession
import app.venues.ticket.repository.ScannerSessionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@Service
class ScannerSessionService(
    private val sessionRepository: ScannerSessionRepository,
    private val qrCodeService: QRCodeService
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
        val qrImage = qrCodeService.generateQrCodeImageBase64(qrContent)

        return savedSession.toDto(qrContent, qrImage)
    }

    @Transactional(readOnly = true)
    override fun validateSession(token: String): ScannerSessionDto? {
        val session = sessionRepository.findBySecretToken(token) ?: return null

        if (!session.isValid()) {
            return null
        }

        return session.toDto(
            qrCodeData = "HIDDEN", // Don't return sensitive QR data on validation
            qrCodeImage = "" // Don't generate image on validation
        )
    }

    private fun ScannerSession.toDto(qrCodeData: String, qrCodeImage: String) = ScannerSessionDto(
        id = id,
        sessionName = sessionName,
        secretToken = secretToken,
        qrCodeData = qrCodeData, // Contains the QR content string
        qrCodeImage = qrCodeImage,
        validUntil = validUntil,
        active = active
    )
}
