package app.venues.user.domain

import app.venues.common.constants.AppConstants
import app.venues.common.domain.AbstractUuidEntity
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.*
import java.time.Instant
import java.util.*

/**
 * User entity representing a registered user in the Venues API system.
 *
 * Users can browse venues, view events, and (in future) make bookings.
 * This entity handles authentication and basic profile information.
 *
 * Security:
 * - Passwords are stored as BCrypt hashes
 * - Email addresses are unique and used for login
 * - Account can be locked after failed login attempts
 * - Soft delete supported (account can be disabled without removal)
 *
 * Audit Trail:
 * - Created and modified timestamps are automatically tracked
 * - Uses JPA Auditing for automatic timestamp management
 */
@Entity
@Table(
    name = "users",
    indexes = [
        Index(name = "idx_user_email", columnList = "email", unique = true),
        Index(name = "idx_user_status", columnList = "status"),
    ]
)
class User(
    /**
     * User's email address - used for login and notifications.
     * Must be unique across the system.
     */
    @Column(nullable = false, unique = true, length = 255)
    val email: String,

    /**
     * BCrypt hashed password.
     * Never store or transmit plain text passwords.
     */
    @Column(nullable = false, length = 255)
    var passwordHash: String,

    /**
     * User's first name.
     */
    @Column(nullable = false, length = AppConstants.Validation.MAX_NAME_LENGTH)
    var firstName: String,

    /**
     * User's last name.
     */
    @Column(nullable = false, length = AppConstants.Validation.MAX_NAME_LENGTH)
    var lastName: String,

    /**
     * User's phone number (optional).
     * Must be unique if provided to prevent duplicate accounts.
     */
    @Column(length = 20)
    var phoneNumber: String? = null,

    /**
     * User's avatar/profile picture URL.
     * Points to stored image in CDN or file storage.
     */
    @Column(length = 512)
    var avatarUrl: String? = null,

    /**
     * Unique referral code for this user.
     * Used for referral program - other users can use this code when registering.
     * Auto-generated on user creation.
     */
    @Column(unique = true, length = 20)
    var referralCode: String? = null,

    /**
     * ID of the user who referred this user (if any).
     * Nullable - not all users are referred by someone.
     */
    @Column
    var referrerId: UUID? = null,

    /**
     * User's role in the system.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var role: UserRole = UserRole.USER,

    /**
     * Current status of the user account.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: UserStatus = UserStatus.ACTIVE,

    /**
     * Number of failed login attempts.
     * Reset to 0 on successful login.
     * Account locked after exceeding threshold.
     */
    @Column(nullable = false)
    var failedLoginAttempts: Int = 0,

    /**
     * Timestamp when account was locked due to failed login attempts.
     * Null if account is not locked.
     */
    @Column
    var lockedUntil: Instant? = null,

    /**
     * Timestamp when the user last logged in successfully.
     */
    @Column
    var lastLoginAt: Instant? = null,

    /**
     * Indicates whether email has been verified.
     * New users must verify their email before full access.
     */
    @Column(nullable = false)
    var emailVerified: Boolean = false,
) : AbstractUuidEntity() {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    /**
     * Gets the user's full name.
     * @return Concatenated first and last name
     */
    fun getFullName(): String = "$firstName $lastName"

    /**
     * Checks if the account is currently locked due to failed login attempts.
     * @return true if locked and lock period hasn't expired
     */
    fun isAccountLocked(): Boolean {
        return lockedUntil?.isAfter(Instant.now()) ?: false
    }

    /**
     * Checks if the account is active and can authenticate.
     * @return true if status is ACTIVE and not locked
     */
    fun canAuthenticate(): Boolean {
        return status == UserStatus.ACTIVE && !isAccountLocked()
    }

    /**
     * Records a failed login attempt.
     * Locks the account if threshold is exceeded.
     */
    fun recordFailedLogin() {
        val previousAttempts = failedLoginAttempts
        failedLoginAttempts++

        logger.debug { "Recording failed login for user $id (email: $email). Attempts: $previousAttempts -> $failedLoginAttempts" }

        if (failedLoginAttempts >= AppConstants.Security.MAX_LOGIN_ATTEMPTS) {
            lockedUntil = Instant.now().plusSeconds(
                (AppConstants.Security.LOCKOUT_DURATION_MINUTES * 60).toLong()
            )
            logger.warn { "Account locked for user $id (email: $email) until $lockedUntil after $failedLoginAttempts failed attempts" }
        }
    }

    /**
     * Records a successful login.
     * Resets failed login counter and updates last login timestamp.
     */
    fun recordSuccessfulLogin() {
        failedLoginAttempts = 0
        lockedUntil = null
        lastLoginAt = Instant.now()
    }

    /**
     * Unlocks the account manually (admin action).
     */
    fun unlockAccount() {
        failedLoginAttempts = 0
        lockedUntil = null
    }
}

