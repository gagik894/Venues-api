package app.venues.user.api

import app.venues.user.api.dto.UserBasicInfoDto

/**
 * Public API contract for User module.
 *
 * This is the Port in Hexagonal Architecture.
 * Defines the stable interface that other modules can use to access user data.
 *
 * Implementation is provided by UserService in the user module.
 */
interface UserApi {

    /**
     * Get basic user information by ID.
     *
     * @param userId User ID
     * @return User basic info or null if not found
     */
    fun getUserBasicInfo(userId: Long): UserBasicInfoDto?

    /**
     * Get user email by ID.
     *
     * @param userId User ID
     * @return User email or null if not found
     */
    fun getUserEmail(userId: Long): String?

    /**
     * Get user full name by ID.
     *
     * @param userId User ID
     * @return Full name (firstName + lastName) or null if not found
     */
    fun getUserFullName(userId: Long): String?

    /**
     * Check if user exists.
     *
     * @param userId User ID
     * @return true if user exists, false otherwise
     */
    fun userExists(userId: Long): Boolean
}

