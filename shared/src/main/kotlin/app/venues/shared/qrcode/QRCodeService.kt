package app.venues.shared.qrcode

import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.util.*

/**
 * Service for QR code generation.
 *
 * Provides utility methods for generating QR codes as images or strings.
 * Used for ticket generation and email embedding.
 */
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
     *
     * @param content The content to encode in the QR code
     * @param width Width of the image in pixels
     * @param height Height of the image in pixels
     * @return Base64-encoded PNG image
     */
    fun generateQrCodeImageBase64(content: String, width: Int = 300, height: Int = 300): String {
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height)
        val outputStream = ByteArrayOutputStream()
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream)
        return Base64.getEncoder().encodeToString(outputStream.toByteArray())
    }
}
