package app.venues.media.service

import app.venues.common.exception.VenuesException
import app.venues.media.api.MediaCategory
import app.venues.media.api.dto.MediaUploadResponse
import app.venues.media.config.MediaProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.net.URI
import java.time.Instant
import java.util.*

/**
 * S3-compatible implementation of MediaStorageService (for Supabase, AWS, etc.).
 */
class S3MediaStorageService(
    private val mediaProperties: MediaProperties
) : MediaStorageService {

    private val logger = KotlinLogging.logger {}
    private val s3Client: S3Client by lazy {
        val s3Props = mediaProperties.s3
        val credentials = AwsBasicCredentials.create(
            s3Props.accessKey ?: throw VenuesException.ConfigurationError("S3 access key is not configured"),
            s3Props.secretKey ?: throw VenuesException.ConfigurationError("S3 secret key is not configured")
        )

        val builder = S3Client.builder()
            .region(Region.of(s3Props.region))
            .credentialsProvider(StaticCredentialsProvider.create(credentials))

        s3Props.endpoint?.let {
            builder.endpointOverride(URI.create(it))
            // Necessary for some S3-compatible providers like Supabase/DigitalOcean
            builder.forcePathStyle(true)
        }

        builder.build()
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

        val key = "${category.folder}/$storedFilename"
        val bucket =
            mediaProperties.s3.bucket ?: throw VenuesException.ConfigurationError("S3 bucket is not configured")

        try {
            val putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.contentType ?: "application/octet-stream")
                .build()

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.inputStream, file.size))
            logger.info { "Stored file in S3: $key in bucket: $bucket" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to store file in S3: $originalFilename" }
            throw VenuesException.InternalError(
                message = "Failed to store file in S3: ${e.message}",
                errorCode = "MEDIA_S3_STORAGE_FAILED"
            )
        }

        val url = buildUrl(key)

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
        val bucket = mediaProperties.s3.bucket ?: return false

        for (category in MediaCategory.entries) {
            try {
                val listResponse = s3Client.listObjectsV2 { it.bucket(bucket).prefix("${category.folder}/$mediaId") }
                val s3Object = listResponse.contents().firstOrNull()

                if (s3Object != null) {
                    s3Client.deleteObject(
                        DeleteObjectRequest.builder()
                            .bucket(bucket)
                            .key(s3Object.key())
                            .build()
                    )
                    logger.info { "Deleted file from S3: ${s3Object.key()}" }
                    return true
                }
            } catch (e: Exception) {
                logger.error(e) { "Error while searching/deleting file from S3: $mediaId" }
            }
        }

        logger.warn { "File not found in S3 for deletion: $mediaId" }
        return false
    }

    override fun isOwnedUrl(url: String): Boolean {
        val publicUrl = mediaProperties.s3.publicUrl ?: return false
        return url.startsWith(publicUrl)
    }

    override fun getBaseUrl(): String {
        return mediaProperties.s3.publicUrl ?: ""
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
                message = "File type '$contentType' is not allowed",
                errorCode = "MEDIA_INVALID_TYPE"
            )
        }
    }

    private fun sanitizeFilename(filename: String): String {
        return filename
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(100)
    }

    private fun buildUrl(key: String): String {
        val publicUrl = mediaProperties.s3.publicUrl ?: return "/$key"
        val cleanPublicUrl = publicUrl.removeSuffix("/")
        return "$cleanPublicUrl/$key"
    }
}

