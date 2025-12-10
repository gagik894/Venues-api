package app.venues.media.service

import app.venues.media.api.MediaApi
import app.venues.media.api.MediaCategory
import app.venues.media.api.dto.MediaUploadResponse
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.util.*

/**
 * Implementation of MediaApi using the configured MediaStorageService.
 */
@Service
class MediaApiImpl(
    private val mediaStorageService: MediaStorageService
) : MediaApi {

    override fun upload(
        file: MultipartFile,
        category: MediaCategory,
        ownerId: UUID?
    ): MediaUploadResponse {
        return mediaStorageService.store(file, category, ownerId)
    }

    override fun uploadMultiple(
        files: List<MultipartFile>,
        category: MediaCategory,
        ownerId: UUID?
    ): List<MediaUploadResponse> {
        return files.map { file ->
            mediaStorageService.store(file, category, ownerId)
        }
    }

    override fun delete(mediaId: UUID): Boolean {
        return mediaStorageService.delete(mediaId)
    }

    override fun isValidMediaUrl(url: String): Boolean {
        return mediaStorageService.isOwnedUrl(url)
    }
}

