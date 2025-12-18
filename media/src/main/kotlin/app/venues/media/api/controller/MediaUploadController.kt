package app.venues.media.api.controller

import app.venues.audit.annotation.AuditMetadata
import app.venues.audit.annotation.Auditable
import app.venues.common.model.ApiResponse
import app.venues.media.api.MediaApi
import app.venues.media.api.MediaCategory
import app.venues.media.api.dto.MediaUploadResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.*
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

/**
 * REST controller for media upload operations.
 *
 * Provides centralized file upload functionality for all modules.
 * Files are organized by category (events, venues, branding, etc.).
 */
@RestController
@RequestMapping("/api/v1/media")
@Tag(name = "Media", description = "File upload and media management operations")
class MediaUploadController(
    private val mediaApi: MediaApi
) {

    /**
     * Upload a single file.
     *
     * Accepts multipart/form-data with a file and optional metadata.
     * Returns the public URL and metadata for the uploaded file.
     */
    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @PreAuthorize("hasRole('STAFF') or hasRole('SUPER_ADMIN')")
    @SecurityRequirement(name = "bearer-jwt")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Upload a file",
        description = "Upload a single file to media storage. Returns the public URL and metadata."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "201",
                description = "File uploaded successfully",
                content = [Content(schema = Schema(implementation = MediaUploadResponse::class))]
            ),
            SwaggerApiResponse(
                responseCode = "400",
                description = "Invalid file or request"
            ),
            SwaggerApiResponse(
                responseCode = "413",
                description = "File too large"
            ),
            SwaggerApiResponse(
                responseCode = "415",
                description = "Unsupported file type"
            )
        ]
    )
    @Auditable(action = "MEDIA_UPLOAD_SINGLE", subjectType = "media", includeVenueId = false)
    fun uploadFile(
        @Parameter(description = "The file to upload", required = true)
        @AuditMetadata("file") @RequestPart("file") file: MultipartFile,

        @Parameter(
            description = "Category for organizing the upload",
            schema = Schema(allowableValues = ["events", "venues", "branding", "profiles", "tickets", "general"])
        )
        @AuditMetadata("category") @RequestParam(defaultValue = "general") category: String,

        @Parameter(description = "Optional owner ID for access control")
        @RequestParam(required = false) ownerId: UUID?,
        @RequestAttribute(name = "staffId", required = false) staffId: UUID?
    ): ApiResponse<MediaUploadResponse> {
        val mediaCategory = parseCategory(category)
        val response = mediaApi.upload(file, mediaCategory, ownerId)
        return ApiResponse.success(response, "File uploaded successfully")
    }

    /**
     * Upload multiple files at once.
     */
    @PostMapping("/upload/batch", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @PreAuthorize("hasRole('STAFF') or hasRole('SUPER_ADMIN')")
    @SecurityRequirement(name = "bearer-jwt")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Upload multiple files",
        description = "Upload multiple files at once. Returns list of public URLs and metadata."
    )
    @Auditable(action = "MEDIA_UPLOAD_BATCH", subjectType = "media_batch", includeVenueId = false)
    fun uploadMultipleFiles(
        @Parameter(description = "The files to upload", required = true)
        @AuditMetadata("files") @RequestPart("files") files: List<MultipartFile>,

        @Parameter(description = "Category for organizing the uploads")
        @AuditMetadata("category") @RequestParam(defaultValue = "general") category: String,

        @Parameter(description = "Optional owner ID for access control")
        @RequestParam(required = false) ownerId: UUID?,
        @RequestAttribute(name = "staffId", required = false) staffId: UUID?
    ): ApiResponse<List<MediaUploadResponse>> {
        val mediaCategory = parseCategory(category)
        val responses = mediaApi.uploadMultiple(files, mediaCategory, ownerId)
        return ApiResponse.success(responses, "${responses.size} files uploaded successfully")
    }

    /**
     * Delete an uploaded file.
     */
    @DeleteMapping("/{mediaId}")
    @PreAuthorize("hasRole('STAFF') or hasRole('SUPER_ADMIN')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(
        summary = "Delete a file",
        description = "Delete a previously uploaded file by its ID."
    )
    @Auditable(action = "MEDIA_DELETE", subjectType = "media", includeVenueId = false)
    fun deleteFile(
        @Parameter(description = "The media ID to delete")
        @PathVariable mediaId: UUID,
        @RequestAttribute(name = "staffId", required = false) staffId: UUID?
    ): ApiResponse<Unit> {
        val deleted = mediaApi.delete(mediaId)
        return if (deleted) {
            ApiResponse.success("File deleted successfully")
        } else {
            ApiResponse.success("File not found or already deleted")
        }
    }

    private fun parseCategory(category: String): MediaCategory {
        return try {
            MediaCategory.entries.find { it.folder == category.lowercase() }
                ?: MediaCategory.GENERAL
        } catch (e: Exception) {
            MediaCategory.GENERAL
        }
    }
}

