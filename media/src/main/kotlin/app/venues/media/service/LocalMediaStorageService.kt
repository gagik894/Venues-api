package app.venues.media.service

import app.venues.common.exception.VenuesException
import app.venues.media.api.MediaCategory
import app.venues.media.api.dto.MediaUploadResponse
import app.venues.media.config.MediaProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.*

/**
 * Local filesystem implementation of MediaStorageService.
 *
 * Stores files in the local filesystem organized by category.
 * In production, this should be replaced with S3 or cloud storage.
 */
@Service
class LocalMediaStorageService(
    private val mediaProperties: MediaProperties
) : MediaStorageService {

    private val logger = KotlinLogging.logger {}
    private val rootDir: Path = Paths.get(mediaProperties.uploadDir)

    init {
        try {
            Files.createDirectories(rootDir)
            logger.info { "Media storage initialized at: ${rootDir.toAbsolutePath()}" }
        } catch (e: Exception) {
            logger.error(e) { "Could not create media upload directory: ${rootDir.toAbsolutePath()}" }
        }
    }

    override fun store(
        file: MultipartFile,
        category: MediaCategory,
        ownerId: UUID?
    ): MediaUploadResponse {
        validateFile(file)

        val mediaId = UUID.randomUUID()
        val originalFilename = sanitizeFilename(file.originalFilename ?: "unknown")
        val extension = originalFilename.substringAfterLast('.', "")
        val storedFilename = if (extension.isNotEmpty()) {
            "$mediaId.$extension"
        } else {
            mediaId.toString()
        }

        val categoryDir = rootDir.resolve(category.folder)
        Files.createDirectories(categoryDir)

        val targetPath = categoryDir.resolve(storedFilename)

        try {
            Files.copy(file.inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING)
            logger.info { "Stored file: $storedFilename in category: ${category.folder}" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to store file: $originalFilename" }
            throw VenuesException.InternalError(
                message = "Failed to store file",
                errorCode = "MEDIA_STORAGE_FAILED"
            )
        }

        val url = buildUrl(category, storedFilename)

        return MediaUploadResponse(
            id = mediaId,
            url = url,
            originalFilename = originalFilename,
            contentType = file.contentType ?: "application/octet-stream",
            size = file.size,
            category = category.folder,
            uploadedAt = Instant.now()
        )
    }

    override fun delete(mediaId: UUID): Boolean {
        // Search for file in all category directories
        for (category in MediaCategory.entries) {
            val categoryDir = rootDir.resolve(category.folder)
            if (!Files.exists(categoryDir)) continue

            try {
                Files.list(categoryDir).use { stream ->
                    val file = stream
                        .filter { it.fileName.toString().startsWith(mediaId.toString()) }
                        .findFirst()

                    if (file.isPresent) {
                        Files.delete(file.get())
                        logger.info { "Deleted file: ${file.get().fileName}" }
                        return true
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Error while searching/deleting file: $mediaId" }
            }
        }

        logger.warn { "File not found for deletion: $mediaId" }
        return false
    }

    override fun isOwnedUrl(url: String): Boolean {
        val baseUrl = getBaseUrl()
        return url.startsWith(baseUrl) || url.startsWith("/uploads/")
    }

    override fun getBaseUrl(): String {
        return mediaProperties.baseUrl.ifEmpty { "/uploads" }
    }

    private fun validateFile(file: MultipartFile) {
        if (file.isEmpty) {
            throw VenuesException.ValidationFailure(
                message = "Cannot upload empty file",
                errorCode = "MEDIA_EMPTY_FILE"
            )
        }

        if (file.size > mediaProperties.maxFileSize) {
            val maxMB = mediaProperties.maxFileSize / (1024 * 1024)
            throw VenuesException.ValidationFailure(
                message = "File size exceeds maximum allowed size of ${maxMB}MB",
                errorCode = "MEDIA_FILE_TOO_LARGE"
            )
        }

        val contentType = file.contentType ?: "application/octet-stream"
        if (contentType !in mediaProperties.allowedTypes) {
            throw VenuesException.ValidationFailure(
                message = "File type '$contentType' is not allowed. Allowed types: ${
                    mediaProperties.allowedImageTypes.joinToString(
                        ", "
                    )
                }",
                errorCode = "MEDIA_INVALID_TYPE"
            )
        }
    }

    private fun sanitizeFilename(filename: String): String {
        return filename
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(100)
    }

    private fun buildUrl(category: MediaCategory, filename: String): String {
        val base = getBaseUrl()
        return "$base/${category.folder}/$filename"
    }
}

