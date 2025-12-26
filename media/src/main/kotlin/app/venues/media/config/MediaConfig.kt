package app.venues.media.config

import app.venues.media.service.LocalMediaStorageService
import app.venues.media.service.MediaStorageService
import app.venues.media.service.S3MediaStorageService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration properties for media storage.
 */
@ConfigurationProperties(prefix = "venues.media")
data class MediaProperties(
    /** Storage type: 'local' or 's3' */
    val storageType: StorageType = StorageType.LOCAL,

    /** Base directory for local storage */
    val uploadDir: String = "uploads",

    /** Base URL for serving uploaded files */
    val baseUrl: String = "",

    /** Maximum file size in bytes (default: 10MB) */
    val maxFileSize: Long = 10 * 1024 * 1024,

    /** S3 specific configuration */
    val s3: S3Properties = S3Properties(),

    /** Allowed MIME types for images */
    val allowedImageTypes: Set<String> = setOf(
        "image/jpeg",
        "image/png",
        "image/gif",
        "image/webp",
        "image/svg+xml"
    ),

    /** Allowed MIME types for documents */
    val allowedDocumentTypes: Set<String> = setOf(
        "application/pdf"
    ),

    /** All allowed MIME types (images + documents) */
    val allowedTypes: Set<String> = allowedImageTypes + allowedDocumentTypes
) {
    enum class StorageType {
        LOCAL, S3
    }

    data class S3Properties(
        val endpoint: String? = null,
        val region: String = "us-east-1",
        val accessKey: String? = null,
        val secretKey: String? = null,
        val bucket: String? = null,
        /** Public URL template for S3 files, e.g., https://xxx.supabase.co/storage/v1/object/public/bucket */
        val publicUrl: String? = null
    )
}

@Configuration
@EnableConfigurationProperties(MediaProperties::class)
class MediaConfig {

    @Bean
    @ConditionalOnProperty(name = ["venues.media.storage-type"], havingValue = "local", matchIfMissing = true)
    fun localMediaStorageService(mediaProperties: MediaProperties): MediaStorageService {
        return LocalMediaStorageService(mediaProperties)
    }

    @Bean
    @ConditionalOnProperty(name = ["venues.media.storage-type"], havingValue = "s3")
    fun s3MediaStorageService(mediaProperties: MediaProperties): MediaStorageService {
        return S3MediaStorageService(mediaProperties)
    }
}
