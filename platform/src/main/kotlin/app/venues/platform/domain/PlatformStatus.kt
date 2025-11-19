package app.venues.platform.domain

/**
 * Platform integration status
 */
enum class PlatformStatus {
    /**
     * Platform is active and can make reservations
     */
    ACTIVE,

    /**
     * Platform is temporarily suspended (can't make new reservations)
     */
    SUSPENDED,

    /**
     * Platform is inactive (disabled by admin)
     */
    INACTIVE
}
