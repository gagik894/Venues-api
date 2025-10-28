package app.venues.user.domain

/**
 * User roles for role-based access control (RBAC).
 *
 * Defines the different levels of access within the system.
 * Spring Security uses these roles for authorization decisions.
 *
 * Role Hierarchy:
 * - USER: Basic user with standard access
 * - ADMIN: System administrator with full access
 *
 * Spring Security Convention:
 * - Roles are prefixed with "ROLE_" internally by Spring Security
 * - Use @PreAuthorize("hasRole('USER')") in code
 * - Database stores values without prefix (USER, ADMIN)
 */
enum class UserRole {
    /**
     * Regular user role.
     *
     * Permissions:
     * - Browse venues and events
     * - Make bookings
     * - Manage own profile
     * - View booking history
     */
    USER,

    /**
     * Administrator role.
     *
     * Permissions:
     * - All USER permissions
     * - Manage all users
     * - Access admin endpoints
     * - View system statistics
     * - Moderate content
     */
    ADMIN;

    /**
     * Gets the Spring Security authority string.
     * @return Role name prefixed with "ROLE_"
     */
    fun getAuthority(): String = "ROLE_$name"
}

