package app.venues.shared.security.util

import app.venues.common.exception.VenuesException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Component
import java.util.*

/**
 * Utility for extracting authenticated principal information from Spring Security Context.
 *
 * Role System:
 * - SUPER_ADMIN: Platform administrator (full system access)
 * - STAFF: Staff member (permissions via organization/venue membership)
 * - USER: Regular customer (self-service only)
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
 *     fun getMyProfile() {
 *         val principalId = securityUtil.getCurrentPrincipalId()
 *         val role = securityUtil.getCurrentRole()
 *         // Use principalId and role...
 *     }
 * }
 * ```
 */
@Component
class SecurityUtil {

    private val logger = KotlinLogging.logger {}

    /**
     * Gets the current authenticated principal's ID from SecurityContext.
     *
     * Works for all principal types (SUPER_ADMIN, STAFF, USER).
     * The ID is stored in the JWT token and extracted during authentication.
     *
     * @return Current principal's UUID
     * @throws VenuesException.AuthenticationFailure if not authenticated
     * @throws VenuesException.InternalError if ID cannot be extracted
     */
    fun getCurrentPrincipalId(): UUID {
        val authentication = getAuthentication()
            ?: throw VenuesException.AuthenticationFailure(
                "User is not authenticated",
                "NOT_AUTHENTICATED"
            )

        return when (val principal = authentication.principal) {
            is Map<*, *> -> {
                // JWT authentication - principal is a Map
                @Suppress("UNCHECKED_CAST")
                val principalMap = principal as? Map<String, Any>
                when (val id = principalMap?.get("id")) {
                    is UUID -> id
                    is String -> try {
                        UUID.fromString(id)
                    } catch (e: IllegalArgumentException) {
                        logger.error { "Invalid UUID format in principal: $id" }
                        throw VenuesException.InternalError(
                            "Invalid principal ID format",
                            "INVALID_PRINCIPAL_ID"
                        )
                    }

                    else -> {
                        logger.error { "ID not found in principal Map. Contents: $principalMap" }
                        throw VenuesException.InternalError(
                            "Principal ID not found in token",
                            "PRINCIPAL_ID_NOT_FOUND"
                        )
                    }
                }
            }

            is UserDetails -> {
                // Fallback: try to extract from authentication
                extractPrincipalIdFromAuthentication(authentication)
            }
            else -> {
                logger.error { "Unexpected principal type: ${principal?.javaClass?.name}" }
                extractPrincipalIdFromAuthentication(authentication)
            }
        }
    }

    /**
     * Gets current principal ID for STAFF members.
     * Alias for getCurrentPrincipalId() with role validation.
     *
     * @return Staff UUID
     * @throws VenuesException.AuthorizationFailure if not a staff member
     */
    fun getCurrentStaffId(): UUID {
        val role = getCurrentRole()
        if (role !in listOf("SUPER_ADMIN", "STAFF")) {
            throw VenuesException.AuthorizationFailure(
                "This action requires staff privileges",
                "STAFF_ROLE_REQUIRED"
            )
        }
        return getCurrentPrincipalId()
    }

    /**
     * Gets current principal ID for regular USER members.
     * Alias for getCurrentPrincipalId() with role validation.
     *
     * @return User UUID
     * @throws VenuesException.AuthorizationFailure if not a regular user
     */
    fun getCurrentUserId(): UUID {
        val role = getCurrentRole()
        if (role != "USER") {
            throw VenuesException.AuthorizationFailure(
                "This action requires user role",
                "USER_ROLE_REQUIRED"
            )
        }
        return getCurrentPrincipalId()
    }

    /**
     * Gets the current authenticated principal's email from SecurityContext.
     *
     * @return Current principal's email
     * @throws VenuesException.AuthenticationFailure if not authenticated
     */
    fun getCurrentEmail(): String {
        val authentication = getAuthentication()
            ?: throw VenuesException.AuthenticationFailure(
                "User is not authenticated",
                "NOT_AUTHENTICATED"
            )

        return when (val principal = authentication.principal) {
            is UserDetails -> principal.username
            is String -> principal
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val principalMap = principal as? Map<String, Any>
                val email = principalMap?.get("email")?.toString()

                if (email == null) {
                    logger.error { "Email not found in principal Map. Contents: $principalMap" }
                    throw VenuesException.InternalError(
                        "Email not found in authentication token",
                        "EMAIL_NOT_FOUND"
                    )
                }

                email
            }

            else -> throw VenuesException.InternalError(
                "Unable to extract email from authentication",
                "EMAIL_EXTRACTION_FAILED"
            )
        }
    }

    /**
     * Gets the current authenticated principal's role from SecurityContext.
     *
     * Returns the platform-level role:
     * - SUPER_ADMIN: Platform administrator
     * - STAFF: Staff member
     * - USER: Regular customer
     *
     * @return Current principal's role (without ROLE_ prefix)
     * @throws VenuesException.AuthenticationFailure if not authenticated
     */
    fun getCurrentRole(): String {
        val authentication = getAuthentication()
            ?: throw VenuesException.AuthenticationFailure(
                "User is not authenticated",
                "NOT_AUTHENTICATED"
            )

        // Extract role from authorities (Spring Security adds ROLE_ prefix)
        val authorities = authentication.authorities
        return authorities.firstOrNull()?.authority?.removePrefix("ROLE_")
            ?: throw VenuesException.InternalError(
                "User role not found in authentication",
                "ROLE_NOT_FOUND"
            )
    }

    /**
     * Checks if the current principal has a specific platform role.
     *
     * @param role Role to check (SUPER_ADMIN, STAFF, USER)
     * @return true if principal has the role
     */
    fun hasRole(role: String): Boolean {
        return try {
            val currentRole = getCurrentRole()
            currentRole.equals(role, ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks if the current principal is a super admin.
     *
     * @return true if principal has SUPER_ADMIN role
     */
    fun isSuperAdmin(): Boolean {
        return hasRole("SUPER_ADMIN")
    }

    /**
     * Checks if the current principal is staff (SUPER_ADMIN or STAFF).
     *
     * @return true if principal is staff member
     */
    fun isStaff(): Boolean {
        return hasRole("SUPER_ADMIN") || hasRole("STAFF")
    }

    /**
     * Checks if the current principal is a regular user.
     *
     * @return true if principal has USER role
     */
    fun isUser(): Boolean {
        return hasRole("USER")
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
     * Extracts principal ID from Authentication object.
     *
     * This method tries multiple approaches to extract the principal ID:
     * 1. From authentication details (if it's a Map)
     * 2. From authentication name (if it's a UUID string)
     * 3. From credentials map
     *
     * @param authentication Authentication object
     * @return Principal UUID
     * @throws VenuesException.InternalError if principal ID cannot be extracted
     */
    private fun extractPrincipalIdFromAuthentication(authentication: Authentication): UUID {
        // Try to get from details map (accept UUID or String)
        (authentication.details as? Map<*, *>)?.let { details ->
            @Suppress("UNCHECKED_CAST")
            val map = details as? Map<String, Any>
            listOf("id", "principalId", "userId", "staffId").forEach { key ->
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
            listOf("id", "principalId", "userId", "staffId").forEach { key ->
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

        throw VenuesException.InternalError(
            "Unable to extract principal ID from authentication context",
            "PRINCIPAL_ID_EXTRACTION_FAILED"
        )
    }
}

