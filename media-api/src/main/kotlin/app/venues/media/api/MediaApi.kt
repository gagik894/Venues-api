package app.venues.media.api

import app.venues.media.api.dto.MediaUploadResponse
import org.springframework.web.multipart.MultipartFile
import java.util.*

/**
 * API interface for media operations.
 * Provides centralized file upload and management functionality.
 */
interface MediaApi {

    /**
     * Upload a file to storage.
     *
     * @param file The file to upload
     * @param category The category/folder for organizing uploads (e.g., "events", "venues", "branding")
     * @param ownerId Optional owner ID for access control (venue ID, event ID, etc.)
     * @return Upload response with URL and metadata
     */
    fun upload(
        file: MultipartFile,
        category: MediaCategory,
        ownerId: UUID? = null
    ): MediaUploadResponse

    /**
     * Upload multiple files at once.
     *
     * @param files List of files to upload
     * @param category The category/folder for organizing uploads
     * @param ownerId Optional owner ID for access control
     * @return List of upload responses
     */
    fun uploadMultiple(
        files: List<MultipartFile>,
        category: MediaCategory,
        ownerId: UUID? = null
    ): List<MediaUploadResponse>

    /**
     * Delete a file by its ID.
     *
     * @param mediaId The media ID to delete
     * @return true if deleted, false if not found
     */
    fun delete(mediaId: UUID): Boolean

    /**
     * Check if a URL belongs to our media storage.
     *
     * @param url The URL to validate
     * @return true if the URL is from our storage
     */
    fun isValidMediaUrl(url: String): Boolean
}

/**
 * Categories for organizing uploaded media.
 */
enum class MediaCategory(val folder: String) {
    EVENT("events"),
    VENUE("venues"),
    BRANDING("branding"),
    PROFILE("profiles"),
    TICKET("tickets"),
    GENERAL("general")
}

