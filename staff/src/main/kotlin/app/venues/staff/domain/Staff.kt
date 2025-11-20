package app.venues.staff.domain

import app.venues.shared.persistence.domain.AbstractUuidEntity
import jakarta.persistence.*
import java.time.Instant
import java.util.*

/**
 * Represents a staff/admin user that can manage an organization and its venues.
 *
 * Staff hierarchy:
 * - OWNER: Full organization access, can manage billing and other staff
 * - ADMIN: Administrative access, can manage venues and events
 * - STAFF: Standard access, can manage day-to-day operations
 * - EVENT_MANAGER: Limited access, restricted to specific events
 *
 * Security features:
 * - Password hashing (BCrypt in service)
 * - Failed login attempt tracking
 * - Account locking mechanisms
 * - Email verification
 * - Temporary access tokens
 */
@Entity
@Table(
    name = "staff",
    indexes = [
        Index(name = "idx_staff_email", columnList = "email", unique = true),
        Index(name = "idx_staff_organization", columnList = "organization_id"),
        Index(name = "idx_staff_status", columnList = "status")
    ]
)
class Staff(
    // --- Mandatory Identity ---
    @Column(name = "email", nullable = false, unique = true)
    var email: String,

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,

    // --- Authorization (Mandatory Enums with Defaults) ---

    @Enumerated(EnumType.STRING)
    @Column(name = "role_level", nullable = false)
    var roleLevel: StaffRoleLevel = StaffRoleLevel.VENUE,

    @Column(name = "organization_id")
    var organizationId: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: StaffStatus = StaffStatus.PENDING_VERIFICATION

) : AbstractUuidEntity() {

    @OneToMany(mappedBy = "staff", cascade = [CascadeType.ALL], orphanRemoval = true)
    var venueScopes: MutableSet<StaffVenueScope> = mutableSetOf()

    // Security fields (protected)
    @Column(name = "failed_login_attempts", nullable = false)
    var failedLoginAttempts: Int = 0
        protected set

    @Column(name = "account_locked_until")
    var accountLockedUntil: Instant? = null
        protected set

    @Column(name = "last_login_at")
    var lastLoginAt: Instant? = null
        protected set

    @Column(name = "verification_token", length = 255)
    var verificationToken: String? = null
        protected set

    @Column(name = "verification_token_expires_at")
    var verificationTokenExpiresAt: Instant? = null
        protected set

    /**
     * Check if this staff account is currently locked due to failed login attempts.
     */
    fun isAccountLocked(): Boolean =
        accountLockedUntil?.isAfter(Instant.now()) ?: false

    /**
     * Check if email verification is pending.
     */
    fun isEmailVerificationPending(): Boolean =
        status == StaffStatus.PENDING_VERIFICATION

    /**
     * Record a successful login.
     */
    fun recordSuccessfulLogin() {
        failedLoginAttempts = 0
        accountLockedUntil = null
        lastLoginAt = Instant.now()
    }

    /**
     * Record a failed login attempt and lock if needed.
     */
    fun recordFailedLoginAttempt() {
        failedLoginAttempts++
        if (failedLoginAttempts >= 5) {
            accountLockedUntil = Instant.now().plusSeconds(900) // 15 minutes
        }
    }

    /**
     * Set email as verified.
     */
    fun verifyEmail() {
        status = StaffStatus.ACTIVE
        verificationToken = null
        verificationTokenExpiresAt = null
    }

    /**
     * Suspend staff account.
     */
    fun suspend() {
        status = StaffStatus.SUSPENDED
    }

    /**
     * Reactivate staff account.
     */
    fun reactivate() {
        status = StaffStatus.ACTIVE
        failedLoginAttempts = 0
        accountLockedUntil = null
    }

    fun canAccessVenue(targetVenueId: UUID, targetVenueOrgId: UUID): Boolean {
        return when (roleLevel) {
            StaffRoleLevel.SYSTEM -> true
            StaffRoleLevel.ORGANIZATION -> this.organizationId == targetVenueOrgId
            StaffRoleLevel.VENUE -> venueScopes.any { it.venueId == targetVenueId }
        }
    }
}

