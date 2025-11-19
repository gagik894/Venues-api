package app.venues.user.domain

import app.venues.common.constants.AppConstants
import app.venues.shared.persistence.domain.AbstractUuidEntity
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
    var status: UserStatus = UserStatus.PENDING_VERIFICATION
        protected set

    @Column(name = "failed_login_attempts", nullable = false)
    @Access(AccessType.FIELD)
    var failedLoginAttempts: Int = 0
        protected set

    @Column(name = "locked_until")
    @Access(AccessType.FIELD)
    var lockedUntil: Instant? = null
        protected set

    @Column(name = "last_login_at")
    @Access(AccessType.FIELD)
    var lastLoginAt: Instant? = null
        protected set

    @Column(name = "email_verified", nullable = false)
    @Access(AccessType.FIELD)
    var emailVerified: Boolean = false
        protected set

    // ===========================================
    // Public Behaviors
    // ===========================================

    fun getFullName(): String = "$firstName $lastName"

    /**
     * Checks if the account is currently locked due to failed login attempts.
     */
    fun isAccountLocked(): Boolean {
        return lockedUntil?.isAfter(Instant.now()) ?: false
    }

    /**
     * Checks if the account is active, verified, and not locked.
     */
    fun canAuthenticate(): Boolean {
        // Business logic for authentication lives here.
        return status == UserStatus.ACTIVE && emailVerified && !isAccountLocked()
    }

    /**
     * Call on a successful login. Resets lockout state and updates last login time.
     */
    fun recordSuccessfulLogin() {
        this.failedLoginAttempts = 0
        this.lockedUntil = null
        this.lastLoginAt = Instant.now()
    }

    /**
     * Call on a failed login. Increments the attempt counter and locks the
     * account if the threshold is breached.
     *
     * @param maxAttempts The configured maximum number of attempts.
     * @param lockoutMinutes The configured duration for the lockout.
     */
    fun recordFailedLogin(maxAttempts: Int, lockoutMinutes: Long) {
        this.failedLoginAttempts++
        if (this.failedLoginAttempts >= maxAttempts) {
            this.lockedUntil = Instant.now().plusSeconds(lockoutMinutes * 60)
            logger.warn { "User account locked: $id (email: $email)" }
        }
    }

    /**
     * Marks the user's email as verified.
     */
    fun verifyEmail() {
        this.emailVerified = true
        this.status = UserStatus.ACTIVE
    }

    /**
     * Suspends the user account.
     */
    fun suspendAccount() {
        this.status = UserStatus.SUSPENDED
    }

    /**
     * Reactivates a suspended user account.
     */
    fun activateAccount() {
        if (this.status == UserStatus.SUSPENDED) {
            this.status = UserStatus.ACTIVE
        }
    }

    /**
     * Deactivates (soft deletes) the user account.
     */
    fun deactivateAccount() {
        this.status = UserStatus.DELETED
    }
}
