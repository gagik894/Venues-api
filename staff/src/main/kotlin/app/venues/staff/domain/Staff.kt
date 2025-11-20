package app.venues.staff.domain

import app.venues.shared.persistence.domain.AbstractUuidEntity
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

    @Column(name = "account_locked_until")
    var accountLockedUntil: Instant? = null,

    @Column(name = "last_login_at")
    var lastLoginAt: Instant? = null,

    @Column(name = "verification_token")
    var verificationToken: String? = null,

    @Column(name = "verification_token_expires_at")
    var verificationTokenExpiresAt: Instant? = null

) : AbstractUuidEntity() {

    @OneToMany(mappedBy = "staff", cascade = [CascadeType.ALL], orphanRemoval = true)
    var memberships: MutableSet<StaffMembership> = mutableSetOf()

    fun isAccountLocked(): Boolean =
        accountLockedUntil?.isAfter(Instant.now()) ?: false

    fun recordSuccessfulLogin() {
        failedLoginAttempts = 0
        accountLockedUntil = null
        lastLoginAt = Instant.now()
    }

    fun recordFailedLoginAttempt(maxAttempts: Int = 5, lockMinutes: Long = 15) {
        failedLoginAttempts++
        if (failedLoginAttempts >= maxAttempts) {
            accountLockedUntil = Instant.now().plusSeconds(lockMinutes * 60)
        }
    }

    fun verifyEmail() {
        status = StaffStatus.ACTIVE
        verificationToken = null
        verificationTokenExpiresAt = null
    }

    fun suspend() {
        status = StaffStatus.SUSPENDED
    }

    fun reactivate() {
        status = StaffStatus.ACTIVE
        failedLoginAttempts = 0
        accountLockedUntil = null
    }
}