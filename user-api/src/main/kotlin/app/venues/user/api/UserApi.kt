package app.venues.user.api

import app.venues.user.api.dto.UserBasicInfoDto
import java.util.*

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
    fun getUserBasicInfo(userId: UUID): UserBasicInfoDto?

    /**
     * Get user email by ID.
     *
     * @param userId User ID
     * @return User email or null if not found
     */
    fun getUserEmail(userId: UUID): String?

    /**
     * Get user full name by ID.
     *
     * @param userId User ID
     * @return Full name (firstName + lastName) or null if not found
     */
    fun getUserFullName(userId: UUID): String?

    /**
     * Check if user exists.
     *
     * @param userId User ID
     * @return true if user exists, false otherwise
     */
    fun userExists(userId: UUID): Boolean

    /**
     * Get user's preferred language for email communications.
     *
     * @param userId User ID
     * @return Language code (e.g., "en", "hy", "ru") or null if not found
     */
    fun getUserLanguage(userId: UUID): String?
}

