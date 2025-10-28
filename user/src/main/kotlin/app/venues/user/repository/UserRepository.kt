package app.venues.user.repository

import app.venues.user.domain.User
import app.venues.user.domain.UserStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository interface for User entity database operations.
 *
 * Spring Data JPA automatically implements this interface at runtime.
 * Provides standard CRUD operations plus custom query methods.
 *
 * Query Methods:
 * - Spring Data JPA derives queries from method names
 * - No implementation code required
 * - Type-safe and compile-time checked
 *
 * Custom Queries:
 * - Use @Query annotation for complex queries
 * - JPQL (Java Persistence Query Language) for database-agnostic queries
 */
@Repository
interface UserRepository : JpaRepository<User, Long> {

    /**
     * Finds a user by email address.
     * Used for authentication and uniqueness checks.
     *
     * @param email The email address to search for
     * @return Optional containing the user if found, empty otherwise
     */
    fun findByEmail(email: String): Optional<User>

    /**
     * Checks if a user exists with the given email.
     * More efficient than findByEmail when only checking existence.
     *
     * @param email The email address to check
     * @return true if a user with this email exists
     */
    fun existsByEmail(email: String): Boolean

    /**
     * Finds all users with a specific status.
     * Useful for admin operations and reporting.
     *
     * @param status The user status to filter by
     * @return List of users with the specified status
     */
    fun findByStatus(status: UserStatus): List<User>

    /**
     * Finds all active users (status = ACTIVE and not locked).
     * Used for admin dashboards and statistics.
     *
     * @return List of active users
     */
    @Query(
        """
        SELECT u FROM User u 
        WHERE u.status = 'ACTIVE' 
        AND (u.lockedUntil IS NULL OR u.lockedUntil < CURRENT_TIMESTAMP)
    """
    )
    fun findAllActiveUsers(): List<User>

    /**
     * Counts users by status.
     * Efficient way to get statistics without loading all users.
     *
     * @param status The status to count
     * @return Number of users with the specified status
     */
    fun countByStatus(status: UserStatus): Long

    /**
     * Finds users by email verification status.
     * Used for sending reminder emails or cleanup operations.
     *
     * @param emailVerified Verification status to filter by
     * @return List of users matching the verification status
     */
    fun findByEmailVerified(emailVerified: Boolean): List<User>

    /**
     * Finds a user by their unique referral code.
     * Used when a new user registers with a referral code.
     *
     * @param referralCode The referral code
     * @return Optional containing the user if found, empty otherwise
     */
    fun findByReferralCode(referralCode: String): Optional<User>

    /**
     * Checks if a referral code already exists.
     * Used when generating unique referral codes.
     *
     * @param referralCode The referral code to check
     * @return true if the code exists
     */
    fun existsByReferralCode(referralCode: String): Boolean

    /**
     * Finds all users who were referred by a specific user.
     * Used for referral program analytics and rewards.
     *
     * @param referrerId ID of the referring user
     * @return List of referred users
     */
    fun findByReferrerId(referrerId: Long): List<User>

    /**
     * Counts how many users were referred by a specific user.
     * Used for referral program metrics and leaderboards.
     *
     * @param referrerId ID of the referring user
     * @return Number of referred users
     */
    fun countByReferrerId(referrerId: Long): Long
}

