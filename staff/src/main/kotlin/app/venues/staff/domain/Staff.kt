package app.venues.staff.domain

import app.venues.shared.persistence.domain.AbstractUuidEntity
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.*
import java.time.Instant


/**
 * Represents a human employee (Identity).
 * Handles Authentication (Login/Password) and Security State.
 * Decoupled from Organizations/Venues (uses StaffMembership for that).
 */
@Entity
@Table(
    name = "staff_identities",
    indexes = [
        Index(name = "idx_staff_email", columnList = "email", unique = true),
        Index(name = "idx_staff_status", columnList = "status")
    ],
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["email"])
    ]
)
@org.hibernate.annotations.Check(constraints = "failed_login_attempts >= 0")
class StaffIdentity(

    @Column(name = "email", nullable = false, unique = true)
    var email: String,

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,

    @Column(name = "first_name")
    var firstName: String? = null,

    @Column(name = "last_name")
    var lastName: String? = null,

    /**
     * True = SaaS Platform Administrator (Full global access)
     */
    @Column(name = "is_platform_super_admin", nullable = false)
    var isPlatformSuperAdmin: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: StaffStatus = StaffStatus.PENDING_VERIFICATION,

    @Column(name = "failed_login_attempts", nullable = false)
    var failedLoginAttempts: Int = 0,

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0,

    @Column(name = "account_locked_until")
    var accountLockedUntil: Instant? = null,

    @Column(name = "last_login_at")
    var lastLoginAt: Instant? = null,

    @Column(name = "verification_token")
    var verificationToken: String? = null,

    @Column(name = "verification_token_expires_at")
    var verificationTokenExpiresAt: Instant? = null,

    /**
     * The staff member's preferred language for email communications.
     * Supports: 'en' (English), 'hy' (Armenian), 'ru' (Russian)
     */
    @Column(name = "preferred_language", length = 5, nullable = false)
    var preferredLanguage: String = "en"

) : AbstractUuidEntity() {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    @OneToMany(mappedBy = "staff", cascade = [CascadeType.ALL], orphanRemoval = true)
    var memberships: MutableSet<StaffMembership> = mutableSetOf()

    // ===========================================
    // Public Behaviors
    // ===========================================

    /**
     * Gets the full name of the staff member.
     * @return Full name or null if either first or last name is missing
     */
    fun getFullName(): String? {
        return if (firstName != null && lastName != null) {
            "$firstName $lastName"
        } else {
            null
        }
    }

    /**
     * Checks if the account is currently locked due to failed login attempts.
     * @return true if account is locked
     */
    fun isAccountLocked(): Boolean {
        return accountLockedUntil?.isAfter(Instant.now()) ?: false
    }

    /**
     * Checks if the account is active and can authenticate.
     *
     * Business logic for authentication:
     * - Status must be ACTIVE or PENDING_VERIFICATION
     * - Account must not be locked
     *
     * @return true if staff can authenticate
     */
    fun canAuthenticate(): Boolean {
        return status in listOf(StaffStatus.ACTIVE, StaffStatus.PENDING_VERIFICATION) &&
                !isAccountLocked()
    }

    /**
     * Call on a successful login. Resets lockout state and updates last login time.
     */
    fun recordSuccessfulLogin() {
        this.failedLoginAttempts = 0
        this.accountLockedUntil = null
        this.lastLoginAt = Instant.now()
    }

    /**
     * Call on a failed login. Increments the attempt counter and locks the
     * account if the threshold is breached.
     *
     * @param maxAttempts The configured maximum number of attempts
     * @param lockoutMinutes The configured duration for the lockout
     */
    fun recordFailedLogin(maxAttempts: Int, lockoutMinutes: Long) {
        // If the account was previously locked but the lock has expired,
        // we should reset the counter so the user gets a fresh set of attempts.
        if (accountLockedUntil != null && accountLockedUntil!!.isBefore(Instant.now())) {
            this.failedLoginAttempts = 0
            this.accountLockedUntil = null
        }

        this.failedLoginAttempts++
        if (this.failedLoginAttempts >= maxAttempts) {
            this.accountLockedUntil = Instant.now().plusSeconds(lockoutMinutes * 60)
            logger.warn { "Staff account locked: $id (email: $email)" }
        }
    }

    /**
     * Marks the staff member's email as verified and activates account.
     */
    fun verifyEmail() {
        this.status = StaffStatus.ACTIVE
        this.verificationToken = null
        this.verificationTokenExpiresAt = null
    }

    /**
     * Suspends the staff account.
     */
    fun suspend() {
        this.status = StaffStatus.SUSPENDED
    }

    /**
     * Reactivates a suspended staff account.
     */
    fun reactivate() {
        if (this.status == StaffStatus.SUSPENDED) {
            this.status = StaffStatus.ACTIVE
        }
        this.failedLoginAttempts = 0
        this.accountLockedUntil = null
    }
}
