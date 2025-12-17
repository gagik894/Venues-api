package app.venues.media.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Configuration properties for media storage.
 */
@ConfigurationProperties(prefix = "venues.media")
data class MediaProperties(
    /** Base directory for local storage */
    val uploadDir: String = "uploads",

    /** Base URL for serving uploaded files (empty = relative paths) */
    val baseUrl: String = "",

    /** Maximum file size in bytes (default: 10MB) */
    val maxFileSize: Long = 10 * 1024 * 1024,

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
)

@Configuration
@EnableConfigurationProperties(MediaProperties::class)
class MediaConfig

