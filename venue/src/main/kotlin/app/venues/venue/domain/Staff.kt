package app.venues.venue.domain

import app.venues.common.domain.AbstractUuidEntity
import jakarta.persistence.*
import java.time.Instant
import java.util.*

/**
 * Represents a Staff account, an "admin" user who can log in to manage a specific venue.
 *
 * This entity is separate from the customer-facing `User` entity, allowing a single
 * person (email) to be both a customer and a staff member without conflict.
 *
 * This entity handles all authentication, permissions, and security (lockout) logic
 * for the admin and white-label panels.
 */
@Entity
@Table(
    name = "staff",
    indexes = [
        Index(name = "idx_staff_email", columnList = "email", unique = true),
        Index(name = "idx_staff_venue_id", columnList = "venue_id")
    ]
)
class Staff(
    /**
     * The login email for the staff member. Unique within the 'staff' table.
     */
    @Column(unique = true, nullable = false, length = 255)
    var email: String,

    /**
     * The BCrypt-hashed password.
     */
    @Column(nullable = false, length = 255)
    var passwordHash: String,

    /**
     * The Venue this staff account has permissions for.
     * This is a direct, coupled link as both entities live in the same module.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    var venue: Venue,
) : AbstractUuidEntity() {

    /**
     * Defines the permission level for this account.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var role: StaffRole = StaffRole.STAFF

    /**
     * If role is EVENT_MANAGER, this must be set.
     * Restricts all actions for this user to this single Event UUID.
     */
    @Column(name = "restricted_to_event_id")
    var restrictedToEventId: UUID? = null

    /**
     * If set, this account is temporary and will be locked after this time.
     */
    @Column
    var expiresAt: Instant? = null

    // ===========================================
    // Account Security & State
    // ===========================================

    /**
     * Number of consecutive failed login attempts.
     */
    @Column(nullable = false)
    var failedLoginAttempts: Int = 0

    /**
     * Timestamp when the account was locked due to failed attempts.
     */
    @Column
    var accountLockedUntil: Instant? = null

    /**
     * Timestamp of the last successful login.
     */
    @Column
    var lastLoginAt: Instant? = null

    /**
     * Whether this staff member has verified their email address.
     */
    @Column(nullable = false)
    var emailVerified: Boolean = false

    /**
     * Secure token used for email verification or password reset.
     */
    @Column(length = 255)
    var verificationToken: String? = null

    /**
     * Expiration time for the verificationToken.
     */
    @Column
    var verificationTokenExpiresAt: Instant? = null

    // ===========================================
    // Business Logic
    // ===========================================

    /**
     * Checks if the account is currently locked (and the lock period is still active).
     */
    fun isAccountLocked(): Boolean {
        return accountLockedUntil?.isAfter(Instant.now()) ?: false
    }

    /**
     * Checks if this is a temporary account and its access has expired.
     */
    fun isTemporaryAccessExpired(): Boolean {
        return expiresAt?.isBefore(Instant.now()) ?: false
    }

    /**
     * Checks if the account is active, not locked, and not expired.
     * This is the main check for authentication.
     */
    fun canAuthenticate(): Boolean {
        return !isAccountLocked() && !isTemporaryAccessExpired()
    }

    /**
     * Resets failed login attempts on a successful login.
     */
    fun resetFailedLoginAttempts() {
        this.failedLoginAttempts = 0
        this.accountLockedUntil = null
        this.lastLoginAt = Instant.now()
    }

    /**
     * Increments failed login attempts and locks the account if the threshold is met.
     *
     * @param maxAttempts The configurable maximum number of attempts (e.g., from AppConstants).
     * @param lockoutMinutes The configurable duration of the lockout (e.g., from AppConstants).
     */
    fun incrementFailedLoginAttempts(maxAttempts: Int, lockoutMinutes: Long) {
        this.failedLoginAttempts++
        if (this.failedLoginAttempts >= maxAttempts) {
            this.accountLockedUntil = Instant.now().plusSeconds(lockoutMinutes * 60)
        }
    }
}