/*
 * Venues API - Government-Sponsored Cultural Venues Portal
 * Copyright (c) 2025 Government Cultural Department
 *
 * Security utility for extracting authenticated user information.
 */

package app.venues.shared.security.util

import app.venues.common.exception.VenuesException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Component
import java.util.*

/**
 * Utility class for extracting authenticated user information from Spring Security Context.
 *
 * This class provides convenient methods to access the current user's information
 * from the SecurityContext without manually extracting it in each controller.
 *
 * Thread Safety:
 * - SecurityContextHolder uses ThreadLocal storage
 * - Each request thread has its own SecurityContext
 * - Safe for concurrent requests
 *
 * Usage:
 * ```kotlin
 * @RestController
 * class MyController(private val securityUtil: SecurityUtil) {
 *     @GetMapping("/me")
 *     fun getCurrentUser() {
 *         val userId = securityUtil.getCurrentUserId()
 *         // Use userId...
 *     }
 * }
 * ```
 */
@Component
class SecurityUtil {

    private val logger = KotlinLogging.logger {}

    /**
     * Gets the current authenticated user's ID from the SecurityContext.
     *
     * The user ID is stored in the JWT token as a custom claim and is extracted
     * during authentication. This method retrieves it from the Authentication principal.
     *
     * @return Current user's ID
     * @throws VenuesException.AuthenticationFailure if user is not authenticated
     * @throws VenuesException.InternalError if user ID cannot be extracted
     */
    fun getCurrentUserId(): UUID {
        val authentication = getAuthentication()
            ?: throw VenuesException.AuthenticationFailure("User is not authenticated")

        // Extract user ID from the principal
        // The principal can be either UserDetails or a Map containing user info
        return when (val principal = authentication.principal) {
            is UserDetails -> {
                // If UserDetails, the username should be the email
                // We need to extract userId from authorities or additional details
                extractUserIdFromAuthentication(authentication)
            }

            is Map<*, *> -> {
                // If Map (from JWT), id should be directly available
                @Suppress("UNCHECKED_CAST")
                val principalMap = principal as? Map<String, Any>
                val id = principalMap?.get("id")?.toString()?.let { UUID.fromString(it) }

                if (id == null) {
                    logger.error { "ID not found in principal Map. Map contents: $principalMap" }
                    throw VenuesException.InternalError("ID not found in authentication token. Available keys: ${principalMap?.keys}")
                }

                id
            }

            else -> {
                // Try to extract from authentication details
                extractUserIdFromAuthentication(authentication)
            }
        }
    }

    /**
     * Gets the current authenticated user's email from the SecurityContext.
     *
     * @return Current user's email
     * @throws VenuesException.AuthenticationFailure if user is not authenticated
     */
    fun getCurrentUserEmail(): String {
        val authentication = getAuthentication()
            ?: throw VenuesException.AuthenticationFailure("User is not authenticated")

        return when (val principal = authentication.principal) {
            is UserDetails -> principal.username
            is String -> principal
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val principalMap = principal as? Map<String, Any>
                val email = principalMap?.get("email")?.toString()

                if (email == null) {
                    logger.error { "Email not found in principal Map. Map contents: $principalMap" }
                    throw VenuesException.InternalError("Email not found in authentication token. Available keys: ${principalMap?.keys}")
                }

                email
            }

            else -> throw VenuesException.InternalError("Unable to extract email from authentication")
        }
    }

    /**
     * Gets the current authenticated user's role from the SecurityContext.
     *
     * @return Current user's role (e.g., "USER", "ADMIN")
     * @throws VenuesException.AuthenticationFailure if user is not authenticated
     */
    fun getCurrentUserRole(): String {
        val authentication = getAuthentication()
            ?: throw VenuesException.AuthenticationFailure("User is not authenticated")

        // Extract role from authorities
        val authorities = authentication.authorities
        return authorities.firstOrNull()?.authority?.removePrefix("ROLE_")
            ?: throw VenuesException.InternalError("User role not found in authentication")
    }

    /**
     * Checks if the current user has a specific role.
     *
     * @param role Role to check (e.g., "ADMIN")
     * @return true if user has the role
     */
    fun hasRole(role: String): Boolean {
        return try {
            val userRole = getCurrentUserRole()
            userRole.equals(role, ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks if the current user is an admin.
     *
     * @return true if user has ADMIN role
     */
    fun isAdmin(): Boolean {
        return hasRole("ADMIN")
    }

    /**
     * Requires that the current authenticated user owns the specified venue.
     *
     * This method verifies that:
     * 1. User is authenticated
     * 2. User has VENUE role
     * 3. User's ID matches the specified venueId
     *
     * @param venueId The venue ID to verify ownership for
     * @throws VenuesException.AuthorizationFailure if user doesn't own the venue
     */
    fun requireVenueOwnership(venueId: UUID) {
        val currentUserId = getCurrentUserId()
        val currentRole = getCurrentUserRole()

        // Verify user has VENUE role
        if (currentRole != "VENUE") {
            throw VenuesException.AuthorizationFailure(
                "Only venue owners can perform this action",
                "INSUFFICIENT_PERMISSIONS"
            )
        }

        // Verify venue ownership (user ID should match venue ID)
        if (currentUserId != venueId) {
            throw VenuesException.AuthorizationFailure(
                "You can only manage your own venue",
                "VENUE_OWNERSHIP_REQUIRED"
            )
        }
    }

    /**
     * Requires that the current authenticated user owns the specified user account.
     *
     * This method verifies that the current user's ID matches the specified userId.
     * Admins bypass this check.
     *
     * @param userId The user ID to verify ownership for
     * @throws VenuesException.AuthorizationFailure if user doesn't own the account
     */
    fun requireUserOwnership(userId: UUID) {
        // Admins can access any user
        if (isAdmin()) {
            return
        }

        val currentUserId = getCurrentUserId()

        // Verify user ownership
        if (currentUserId != userId) {
            throw VenuesException.AuthorizationFailure(
                "You can only access your own account",
                "USER_OWNERSHIP_REQUIRED"
            )
        }
    }

    /**
     * Checks if the current user is authenticated.
     *
     * @return true if user is authenticated
     */
    fun isAuthenticated(): Boolean {
        val authentication = getAuthentication()
        return authentication != null && authentication.isAuthenticated
    }

    /**
     * Gets the current Authentication object from SecurityContext.
     *
     * @return Authentication object or null if not authenticated
     */
    private fun getAuthentication(): Authentication? {
        return SecurityContextHolder.getContext()?.authentication
    }

    /**
     * Extracts user ID from Authentication object.
     *
     * This method tries multiple approaches to extract the user ID:
     * 1. From authentication details (if it's a Map)
     * 2. From authentication name (if it's a number)
     * 3. From custom attributes
     *
     * @param authentication Authentication object
     * @return User ID
     * @throws VenuesException.InternalError if user ID cannot be extracted
     */
    private fun extractUserIdFromAuthentication(authentication: Authentication): UUID {
        // Try to get from details map (accept UUID or String)
        (authentication.details as? Map<*, *>)?.let { details ->
            @Suppress("UNCHECKED_CAST")
            val map = details as? Map<String, Any>
            listOf("userId", "id").forEach { key ->
                map?.get(key)?.let { raw ->
                    when (raw) {
                        is UUID -> return raw
                        is String -> {
                            try {
                                return UUID.fromString(raw)
                            } catch (e: IllegalArgumentException) {
                                logger.debug { "Unable to parse UUID from details[$key]: $raw" }
                            }
                        }
                    }
                }
            }
        }

        // Try to get from authentication name (if it's a UUID string)
        authentication.name?.let { name ->
            try {
                return UUID.fromString(name)
            } catch (e: IllegalArgumentException) {
                logger.debug { "Unable to parse UUID from authentication.name: $name" }
            }
        }

        // Try to get from credentials map
        (authentication.credentials as? Map<*, *>)?.let { creds ->
            @Suppress("UNCHECKED_CAST")
            val map = creds as? Map<String, Any>
            listOf("userId", "id").forEach { key ->
                map?.get(key)?.let { raw ->
                    when (raw) {
                        is UUID -> return raw
                        is String -> {
                            try {
                                return UUID.fromString(raw)
                            } catch (e: IllegalArgumentException) {
                                logger.debug { "Unable to parse UUID from credentials[$key]: $raw" }
                            }
                        }
                    }
                }
            }
        }

        throw VenuesException.InternalError("Unable to extract user ID (UUID) from authentication context")
    }
}

