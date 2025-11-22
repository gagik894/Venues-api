package app.venues.event.service

import app.venues.common.exception.VenuesException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*

/**
 * Service for handling image uploads.
 *
 * Currently stores files locally. In production, this should be replaced
 * with an S3 or cloud storage implementation.
 */
@Service
class ImageStorageService {

    private val logger = KotlinLogging.logger {}
    private val uploadDir = Paths.get("uploads/events")

    init {
        try {
            Files.createDirectories(uploadDir)
        } catch (e: Exception) {
            logger.error(e) { "Could not create upload directory" }
        }
    }

    /**
     * Store a file and return its URL/path.
     *
     * @param file The file to store.
     * @return The relative URL/path to the stored file.
     */
    fun store(file: MultipartFile): String {
        if (file.isEmpty) {
            throw VenuesException.ValidationFailure("Failed to store empty file")
        }

        try {
            val filename = "${UUID.randomUUID()}-${file.originalFilename}"
            val targetLocation = uploadDir.resolve(filename)

            Files.copy(file.inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING)

            // In a real app, this would return a full CDN URL.
            // For now, we return a relative path that could be served by a static resource handler.
            return "/uploads/events/$filename"
        } catch (e: Exception) {
            logger.error(e) { "Failed to store file ${file.originalFilename}" }
            throw VenuesException.ValidationFailure("Failed to store file")
        }
    }
}
