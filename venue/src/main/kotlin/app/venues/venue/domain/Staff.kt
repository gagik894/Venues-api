package app.venues.venue.domain

import app.venues.shared.persistence.domain.AbstractUuidEntity
import jakarta.persistence.*
import java.time.Instant
import java.util.*

/**
 * Represents a Staff account, an "admin" user who can log in to manage a specific venue.
 * This entity is separate from the customer-facing `User` entity.
 *
 * @param email The login email for the staff member. Unique within the 'staff' table.
 * @param passwordHash The BCrypt-hashed password.
 * @param venue The Venue this staff account has permissions for.
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
    @Column(name = "email", unique = true, nullable = false, length = 255)
    var email: String,

    @Column(name = "password_hash", nullable = false, length = 255)
    var passwordHash: String,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    var venue: Venue,

) : AbstractUuidEntity() {

    // ===========================================
    // Internal State (Encapsulated)
    // ===========================================

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @Access(AccessType.FIELD)
    var role: StaffRole = StaffRole.STAFF
        protected set

    @Column(name = "restricted_to_event_id")
    @Access(AccessType.FIELD)
    var restrictedToEventId: UUID? = null
        protected set

    @Column(name = "expires_at")
    @Access(AccessType.FIELD)
    var expiresAt: Instant? = null
        protected set

    @Column(name = "failed_login_attempts", nullable = false)
    @Access(AccessType.FIELD)
    var failedLoginAttempts: Int = 0
        protected set

    @Column(name = "account_locked_until")
    @Access(AccessType.FIELD)
    var accountLockedUntil: Instant? = null
        protected set

    @Column(name = "last_login_at")
    @Access(AccessType.FIELD)
    var lastLoginAt: Instant? = null
        protected set

    @Column(name = "email_verified", nullable = false)
    @Access(AccessType.FIELD)
    var emailVerified: Boolean = false
        protected set

    @Column(name = "verification_token", length = 255)
    @Access(AccessType.FIELD)
    var verificationToken: String? = null
        protected set

    @Column(name = "verification_token_expires_at")
    @Access(AccessType.FIELD)
    var verificationTokenExpiresAt: Instant? = null
        protected set

    // ===========================================
    // Public Behaviors
    // ===========================================

    private fun isAccountLocked(): Boolean {
        return accountLockedUntil?.isAfter(Instant.now()) ?: false
    }

    private fun isTemporaryAccessExpired(): Boolean {
        return expiresAt?.isBefore(Instant.now()) ?: false
    }

    /**
     * Public, read-only check to see if this user can log in.
     */
    fun canAuthenticate(): Boolean {
        return emailVerified && !isAccountLocked() && !isTemporaryAccessExpired()
    }

    /**
     * Call on a successful login. Resets lockout state.
     */
    fun recordSuccessfulLogin() {
        this.failedLoginAttempts = 0
        this.accountLockedUntil = null
        this.lastLoginAt = Instant.now()
    }

    /**
     * Call on a failed login. Increments counter and locks if needed.
     *
     * @param maxAttempts The configurable maximum number of attempts.
     * @param lockoutMinutes The configurable duration for the lockout.
     */
    fun recordFailedLoginAttempt(maxAttempts: Int, lockoutMinutes: Long) {
        this.failedLoginAttempts++
        if (this.failedLoginAttempts >= maxAttempts) {
            this.accountLockedUntil = Instant.now().plusSeconds(lockoutMinutes * 60)
        }
    }

    /**
     * Grants temporary access for a single event.
     *
     * @param eventId The UUID of the event to restrict access to.
     * @param expires The exact time the access expires.
     */
    fun grantTemporaryEventAccess(eventId: UUID, expires: Instant) {
        this.role = StaffRole.EVENT_MANAGER
        this.restrictedToEventId = eventId
        this.expiresAt = expires
    }

    /**
     * Marks the account's email as verified and clears tokens.
     */
    fun verifyEmail() {
        this.emailVerified = true
        this.verificationToken = null
        this.verificationTokenExpiresAt = null
    }

    /**
     * Generates and sets a new verification token.
     *
     * @param token The secure token string.
     * @param expires The exact time the token expires.
     */
    fun setVerificationToken(token: String, expires: Instant) {
        this.verificationToken = token
        this.verificationTokenExpiresAt = expires
    }
}
