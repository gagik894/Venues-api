package app.venues.media.service

import app.venues.media.api.MediaCategory
import app.venues.media.api.dto.MediaUploadResponse
import org.springframework.web.multipart.MultipartFile
import java.util.*

/**
 * Interface for media storage operations.
 * Implementations can target local storage, S3, GCS, etc.
 */
interface MediaStorageService {

    /**
     * Store a file and return its public URL.
     *
     * @param file The file to store
     * @param category Category for organizing storage
     * @param ownerId Optional owner for access control
     * @return Upload response with URL and metadata
     */
    fun store(
        file: MultipartFile,
        category: MediaCategory,
        ownerId: UUID? = null
    ): MediaUploadResponse

    /**
     * Delete a file by its ID.
     *
     * @param mediaId The media ID to delete
     * @return true if deleted successfully
     */
    fun delete(mediaId: UUID): Boolean

    /**
     * Check if a URL belongs to this storage service.
     *
     * @param url The URL to check
     * @return true if the URL is from this storage
     */
    fun isOwnedUrl(url: String): Boolean

    /**
     * Get the base URL for this storage service.
     */
    fun getBaseUrl(): String
}

