package app.venues.user.domain

/**
 * User account status for lifecycle management.
 *
 * Tracks the current state of a user account through its lifecycle.
 * Supports soft delete pattern (suspended/deleted accounts remain in database).
 */
enum class UserStatus {
    /**
     * Active account in good standing.
     * User can authenticate and use all features.
     */
    ACTIVE,

    /**
     * Pending email verification.
     * User has registered but not verified their email address.
     * Limited access until verification is complete.
     */
    PENDING_VERIFICATION,

    /**
     * Account temporarily suspended.
     * User cannot authenticate until account is reactivated.
     * Used for:
     * - Terms of service violations
     * - Temporary administrative actions
     * - Security incidents
     */
    SUSPENDED,

    /**
     * Account permanently deleted (soft delete).
     * User cannot authenticate.
     * Data retained for audit/legal purposes.
     * Cannot be reactivated - user must create new account.
     */
    DELETED
}

