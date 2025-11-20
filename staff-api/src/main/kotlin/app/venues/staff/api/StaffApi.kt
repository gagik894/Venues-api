package app.venues.staff.api

import java.util.*

/**
 * Public API for Staff module.
 */
interface StaffApi {

    /**
     * Check if a user has a specific permission.
     */
    fun hasPermission(userId: UUID, permission: String): Boolean
}
