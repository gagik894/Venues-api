package app.venues.media.api.dto

import java.time.Instant
import java.util.*

/**
 * Response returned after a successful media upload.
 */
data class MediaUploadResponse(
    /** Unique identifier for the uploaded media */
    val id: UUID,

    /** Public URL to access the media */
    val url: String,

    /** Original filename */
    val originalFilename: String,

    /** MIME type of the file */
    val contentType: String,

    /** File size in bytes */
    val size: Long,

    /** Category the file was uploaded to */
    val category: String,

    /** When the file was uploaded */
    val uploadedAt: Instant
)

/**
 * Request for uploading media (used in multipart forms).
 */
data class MediaUploadRequest(
    /** Category for organizing the upload */
    val category: String = "general",

    /** Optional owner ID for access control */
    val ownerId: UUID? = null
)

/**
 * Media metadata for listing/querying.
 */
data class MediaMetadata(
    val id: UUID,
    val url: String,
    val originalFilename: String,
    val contentType: String,
    val size: Long,
    val category: String,
    val ownerId: UUID?,
    val uploadedAt: Instant
)

