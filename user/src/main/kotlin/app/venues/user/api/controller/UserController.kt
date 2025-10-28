package app.venues.user.api.controller

import app.venues.common.model.ApiResponse
import app.venues.shared.security.util.SecurityUtil
import app.venues.user.api.dto.PasswordChangeRequest
import app.venues.user.api.dto.UserResponse
import app.venues.user.api.dto.UserUpdateRequest
import app.venues.user.api.mapper.UserMapper
import app.venues.user.service.UserService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

/**
 * REST controller for user profile management.
 *
 * Endpoints:
 * - GET /api/v1/users/me - Get current user profile
 * - PUT /api/v1/users/me - Update current user profile
 * - PUT /api/v1/users/me/password - Change password
 * - GET /api/v1/users/{id} - Get user by ID (Admin only)
 * - GET /api/v1/users - Get all users (Admin only)
 *
 * All endpoints require authentication.
 * Admin endpoints require ADMIN role.
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User Management", description = "User profile management endpoints")
@SecurityRequirement(name = "bearerAuth")
class UserController(
    private val userService: UserService,
    private val securityUtil: SecurityUtil
) {

    private val logger = KotlinLogging.logger {}

    /**
     * Gets the current authenticated user's profile.
     *
     * Extracts user ID from JWT token in SecurityContext.
     *
     * @return Current user's profile information
     */
    @GetMapping("/me")
    @Operation(
        summary = "Get current user profile",
        description = "Returns profile information for the authenticated user"
    )
    fun getCurrentUser(): ResponseEntity<ApiResponse<UserResponse>> {
        val userId = securityUtil.getCurrentUserId()
        logger.debug { "Get current user profile request: userId=$userId" }

        val user = userService.getUserById(userId)
        val response = UserMapper.toResponse(user)

        return ResponseEntity.ok(ApiResponse.success(response))
    }

    /**
     * Updates the current authenticated user's profile.
     *
     * Only provided fields will be updated.
     * Cannot change email, role, or status through this endpoint.
     *
     * @param request Update request with new values
     * @return Updated user profile
     */
    @PutMapping("/me")
    @Operation(
        summary = "Update current user profile",
        description = "Update profile information (name, phone number)"
    )
    fun updateCurrentUser(
        @Valid @RequestBody request: UserUpdateRequest
    ): ResponseEntity<ApiResponse<UserResponse>> {
        val userId = securityUtil.getCurrentUserId()
        logger.info { "Update user profile request: userId=$userId" }

        val user = userService.updateUserProfile(userId, request)
        val response = UserMapper.toResponse(user)

        return ResponseEntity.ok(ApiResponse.success(response))
    }

    /**
     * Changes the current user's password.
     *
     * Requires current password for security.
     *
     * @param request Password change request
     * @return Success message
     */
    @PutMapping("/me/password")
    @Operation(
        summary = "Change password",
        description = "Change password for the authenticated user. Requires current password."
    )
    fun changePassword(
        @Valid @RequestBody request: PasswordChangeRequest
    ): ResponseEntity<ApiResponse<String>> {
        val userId = securityUtil.getCurrentUserId()
        logger.info { "Change password request: userId=$userId" }

        userService.changePassword(userId, request.currentPassword, request.newPassword)

        return ResponseEntity.ok(ApiResponse.success("Password changed successfully"))
    }

    /**
     * Gets a user by ID.
     * Admin only.
     *
     * @param id User ID
     * @return User profile
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Get user by ID (Admin)",
        description = "Returns user profile for any user. Requires ADMIN role."
    )
    fun getUserById(
        @PathVariable id: Long
    ): ResponseEntity<ApiResponse<UserResponse>> {
        logger.debug { "Get user by ID request: userId=$id" }

        val user = userService.getUserById(id)
        val response = UserMapper.toResponse(user)

        return ResponseEntity.ok(ApiResponse.success(response))
    }

    /**
     * Gets all users.
     * Admin only.
     *
     * @return List of all users
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Get all users (Admin)",
        description = "Returns list of all users. Requires ADMIN role."
    )
    fun getAllUsers(): ResponseEntity<ApiResponse<List<UserResponse>>> {
        logger.debug { "Get all users request" }

        val users = userService.getAllUsers()
        val response = UserMapper.toResponseList(users)

        return ResponseEntity.ok(ApiResponse.success(response))
    }
}

