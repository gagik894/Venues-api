package app.venues.user.domain

import app.venues.common.constants.AppConstants
import app.venues.common.domain.AbstractUuidEntity
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.*
import java.time.Instant
import java.util.*

/**
 * Represents a customer `User` account for the public-facing application.
 *
 * This entity is separate from `Staff` (venue admins) to allow a single
 * person to have both a customer account and a staff account with the same email.
 * It manages all customer-specific authentication and profile data.
 *
 * @param email The user's unique login email address.
 * @param passwordHash The BCrypt-hashed password.
 * @param firstName The user's first name.
 * @param lastName The user's last name.
 */
@Entity
@Table(
    name = "users",
    indexes = [
        Index(name = "idx_user_email", columnList = "email", unique = true),
        Index(name = "idx_user_status", columnList = "status"),
        Index(name = "idx_user_referrer_id", columnList = "referrer_id")
    ]
)
class User(
    @Column(name = "email", nullable = false, unique = true, length = 255)
    var email: String,

    @Column(name = "password_hash", nullable = false, length = 255)
    var passwordHash: String,

    @Column(name = "first_name", nullable = false, length = AppConstants.Validation.MAX_NAME_LENGTH)
    var firstName: String,

    @Column(name = "last_name", nullable = false, length = AppConstants.Validation.MAX_NAME_LENGTH)
    var lastName: String,

    @Column(name = "phone_number", length = 20)
    var phoneNumber: String? = null,

    @Column(name = "avatar_url", length = 512)
    var avatarUrl: String? = null,

    @Column(name = "referral_code", unique = true, length = 20)
    var referralCode: String? = null,

    /**
     * The `id` of the `User` who referred this user.
     * This is a cross-module (self-referential) link, so we use the ID.
     */
    @Column(name = "referrer_id")
    var referrerId: UUID? = null,

    ) : AbstractUuidEntity() {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    /**
     * The user's role, e.g., USER or ADMIN.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    var role: UserRole = UserRole.USER

    // ===========================================
    // Internal State (Encapsulated)
    // ===========================================

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Access(AccessType.FIELD)
    private var _status: UserStatus = UserStatus.PENDING_VERIFICATION

    /**
     * Public, read-only view of the user's current status.
     */
    val status: UserStatus
        get() = _status

    @Column(name = "faild_login_attemps", nullable = false)
    @Access(AccessType.FIELD)
    private var _failedLoginAttempts: Int = 0

    @Column(name = "locked_until")
    @Access(AccessType.FIELD)
    private var _lockedUntil: Instant? = null

    @Column(name = "last_login_at")
    @Access(AccessType.FIELD)
    private var _lastLoginAt: Instant? = null

    /**
     * Public, read-only view of the last login time.
     */
    val lastLoginAt: Instant?
        get() = _lastLoginAt

    @Column(name = "email_verified", nullable = false)
    @Access(AccessType.FIELD)
    private var _emailVerified: Boolean = false

    /**
     * Public, read-only view of email verification status.
     */
    val emailVerified: Boolean
        get() = _emailVerified

    // ===========================================
    // Public Behaviors
    // ===========================================

    fun getFullName(): String = "$firstName $lastName"

    /**
     * Checks if the account is currently locked due to failed login attempts.
     */
    fun isAccountLocked(): Boolean {
        return _lockedUntil?.isAfter(Instant.now()) ?: false
    }

    /**
     * Checks if the account is active, verified, and not locked.
     */
    fun canAuthenticate(): Boolean {
        // Business logic for authentication lives here.
        return _status == UserStatus.ACTIVE && _emailVerified && !isAccountLocked()
    }

    /**
     * Call on a successful login. Resets lockout state and updates last login time.
     */
    fun recordSuccessfulLogin() {
        this._failedLoginAttempts = 0
        this._lockedUntil = null
        this._lastLoginAt = Instant.now()
    }

    /**
     * Call on a failed login. Increments the attempt counter and locks the
     * account if the threshold is breached.
     *
     * @param maxAttempts The configured maximum number of attempts.
     * @param lockoutMinutes The configured duration for the lockout.
     */
    fun recordFailedLogin(maxAttempts: Int, lockoutMinutes: Long) {
        this._failedLoginAttempts++
        if (this._failedLoginAttempts >= maxAttempts) {
            this._lockedUntil = Instant.now().plusSeconds(lockoutMinutes * 60)
            logger.warn { "User account locked: $id (email: $email)" }
        }
    }

    /**
     * Marks the user's email as verified.
     */
    fun verifyEmail() {
        this._emailVerified = true
        this._status = UserStatus.ACTIVE
    }

    /**
     * Suspends the user account.
     */
    fun suspendAccount() {
        this._status = UserStatus.SUSPENDED
    }

    /**
     * Reactivates a suspended user account.
     */
    fun activateAccount() {
        if (this._status == UserStatus.SUSPENDED) {
            this._status = UserStatus.ACTIVE
        }
    }

    /**
     * Deactivates (soft deletes) the user account.
     */
    fun deactivateAccount() {
        this._status = UserStatus.DELETED
    }
}