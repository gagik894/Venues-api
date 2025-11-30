package app.venues.ticket.service

import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.util.*

@Service
class QRCodeService {

    private val writer = QRCodeWriter()

    /**
     * Generates a unique QR code string for a ticket.
     * Format: TKT-{UUID}
     */
    fun generateTicketQrCode(): String {
        return "TKT-${UUID.randomUUID()}"
    }

    /**
     * Generates a QR code image (PNG) as Base64 string.
     */
    fun generateQrCodeImageBase64(content: String, width: Int = 300, height: Int = 300): String {
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height)
        val outputStream = ByteArrayOutputStream()
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream)
        return Base64.getEncoder().encodeToString(outputStream.toByteArray())
    }
}
