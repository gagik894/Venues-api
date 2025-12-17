package app.venues.ticket.api.mapper

import app.venues.ticket.api.dto.ScannerSessionDto
import app.venues.ticket.domain.ScannerSession
import org.springframework.stereotype.Component

@Component
class ScannerSessionMapper {

    fun toDto(session: ScannerSession, qrCodeData: String): ScannerSessionDto {
        return ScannerSessionDto(
            id = session.id,
            sessionName = session.sessionName,
            secretToken = session.secretToken,
            qrCodeData = qrCodeData,
            validUntil = session.validUntil,
            active = session.active
        )
    }
}
