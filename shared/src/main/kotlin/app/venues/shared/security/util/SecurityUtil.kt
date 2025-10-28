/*
 * Venues API - Government-Sponsored Cultural Venues Portal
 * Copyright (c) 2025 Government Cultural Department
 *
 * Security utility for extracting authenticated user information.
 */

package app.venues.shared.security.util

import app.venues.common.exception.VenuesException
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Component

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
    fun getCurrentUserId(): Long {
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
                // If Map (from JWT), userId should be directly available
                @Suppress("UNCHECKED_CAST")
                (principal as? Map<String, Any>)?.get("userId")?.toString()?.toLongOrNull()
                    ?: throw VenuesException.InternalError("User ID not found in authentication token")
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
                (principal as? Map<String, Any>)?.get("sub")?.toString()
                    ?: throw VenuesException.InternalError("Email not found in authentication token")
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
    private fun extractUserIdFromAuthentication(authentication: Authentication): Long {
        // Try to get from details
        (authentication.details as? Map<*, *>)?.let { details ->
            @Suppress("UNCHECKED_CAST")
            (details as? Map<String, Any>)?.get("userId")?.toString()?.toLongOrNull()?.let {
                return it
            }
        }

        // Try to get from name (if it's numeric)
        authentication.name?.toLongOrNull()?.let {
            return it
        }

        // Try to get from credentials
        (authentication.credentials as? Map<*, *>)?.let { creds ->
            @Suppress("UNCHECKED_CAST")
            (creds as? Map<String, Any>)?.get("userId")?.toString()?.toLongOrNull()?.let {
                return it
            }
        }

        throw VenuesException.InternalError("Unable to extract user ID from authentication context")
    }
}

