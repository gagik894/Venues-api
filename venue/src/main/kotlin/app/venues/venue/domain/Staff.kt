package app.venues.venue.domain

import app.venues.common.domain.AbstractUuidEntity
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
    private var _role: StaffRole = StaffRole.STAFF

    val role: StaffRole
        get() = _role

    @Column(name = "restricted_to_event_id")
    @Access(AccessType.FIELD)
    private var _restrictedToEventId: UUID? = null

    val restrictedToEventId: UUID?
        get() = _restrictedToEventId

    @Column(name = "expires_at")
    @Access(AccessType.FIELD)
    private var _expiresAt: Instant? = null

    val expiresAt: Instant?
        get() = _expiresAt

    @Column(name = "failed_login_attempts", nullable = false)
    @Access(AccessType.FIELD)
    private var _failedLoginAttempts: Int = 0

    @Column(name = "account_locked_until")
    @Access(AccessType.FIELD)
    private var _accountLockedUntil: Instant? = null

    @Column(name = "last_login_at")
    @Access(AccessType.FIELD)
    private var _lastLoginAt: Instant? = null

    val lastLoginAt: Instant?
        get() = _lastLoginAt

    @Column(name = "email_verified", nullable = false)
    @Access(AccessType.FIELD)
    private var _emailVerified: Boolean = false

    val emailVerified: Boolean
        get() = _emailVerified

    @Column(name = "verification_token", length = 255)
    @Access(AccessType.FIELD)
    private var _verificationToken: String? = null

    @Column(name = "verification_token_expires_at")
    @Access(AccessType.FIELD)
    private var _verificationTokenExpiresAt: Instant? = null

    // ===========================================
    // Public Behaviors
    // ===========================================

    private fun isAccountLocked(): Boolean {
        return _accountLockedUntil?.isAfter(Instant.now()) ?: false
    }

    private fun isTemporaryAccessExpired(): Boolean {
        return _expiresAt?.isBefore(Instant.now()) ?: false
    }

    /**
     * Public, read-only check to see if this user can log in.
     */
    fun canAuthenticate(): Boolean {
        return _emailVerified && !isAccountLocked() && !isTemporaryAccessExpired()
    }

    /**
     * Call on a successful login. Resets lockout state.
     */
    fun recordSuccessfulLogin() {
        this._failedLoginAttempts = 0
        this._accountLockedUntil = null
        this._lastLoginAt = Instant.now()
    }

    /**
     * Call on a failed login. Increments counter and locks if needed.
     *
     * @param maxAttempts The configurable maximum number of attempts.
     * @param lockoutMinutes The configurable duration for the lockout.
     */
    fun recordFailedLoginAttempt(maxAttempts: Int, lockoutMinutes: Long) {
        this._failedLoginAttempts++
        if (this._failedLoginAttempts >= maxAttempts) {
            this._accountLockedUntil = Instant.now().plusSeconds(lockoutMinutes * 60)
        }
    }

    /**
     * Grants temporary access for a single event.
     *
     * @param eventId The UUID of the event to restrict access to.
     * @param expires The exact time the access expires.
     */
    fun grantTemporaryEventAccess(eventId: UUID, expires: Instant) {
        this._role = StaffRole.EVENT_MANAGER
        this._restrictedToEventId = eventId
        this._expiresAt = expires
    }

    /**
     * Marks the account's email as verified and clears tokens.
     */
    fun verifyEmail() {
        this._emailVerified = true
        this._verificationToken = null
        this._verificationTokenExpiresAt = null
    }

    /**
     * Generates and sets a new verification token.
     *
     * @param token The secure token string.
     * @param expires The exact time the token expires.
     */
    fun setVerificationToken(token: String, expires: Instant) {
        this._verificationToken = token
        this._verificationTokenExpiresAt = expires
    }
}