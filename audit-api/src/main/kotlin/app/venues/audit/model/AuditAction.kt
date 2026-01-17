package app.venues.audit.model

/**
 * All auditable staff actions in the system.
 * Each action has a default category and severity for automatic classification.
 */
enum class AuditAction(
    val category: AuditCategory,
    val severity: AuditSeverity,
    val descriptionTemplate: String? = null
) {
    // =========================================================================
    // SECURITY - Authentication & Authorization
    // =========================================================================
    STAFF_LOGIN(AuditCategory.SECURITY, AuditSeverity.INFO, "Staff logged in"),
    STAFF_LOGOUT(AuditCategory.SECURITY, AuditSeverity.INFO, "Staff logged out"),
    STAFF_LOGIN_FAILED(AuditCategory.SECURITY, AuditSeverity.CRITICAL, "Failed login attempt"),
    STAFF_REGISTER(AuditCategory.SECURITY, AuditSeverity.IMPORTANT, "Staff account registered"),
    STAFF_VERIFY_EMAIL(AuditCategory.SECURITY, AuditSeverity.INFO, "Staff email verified"),
    STAFF_ACCEPT_INVITE(AuditCategory.SECURITY, AuditSeverity.INFO, "Staff accepted venue invitation"),
    STAFF_DIRECT_CREATE(AuditCategory.SECURITY, AuditSeverity.IMPORTANT, "Staff account created directly"),
    STAFF_STATUS_UPDATED(AuditCategory.SECURITY, AuditSeverity.CRITICAL, "Staff account status changed"),
    STAFF_GRANT_VENUE_PERMISSION(AuditCategory.SECURITY, AuditSeverity.CRITICAL, "Venue permission granted to staff"),
    STAFF_VENUE_ROLE_UPDATED(AuditCategory.SECURITY, AuditSeverity.CRITICAL, "Staff venue role updated"),
    STAFF_SET_SUPER_ADMIN(AuditCategory.SECURITY, AuditSeverity.CRITICAL, "Super admin status changed"),
    USER_LOGIN(AuditCategory.SECURITY, AuditSeverity.INFO, "User logged in"),
    USER_REGISTER(AuditCategory.SECURITY, AuditSeverity.INFO, "User account registered"),
    USER_PASSWORD_CHANGE(AuditCategory.SECURITY, AuditSeverity.IMPORTANT, "User password changed"),
    USER_PROFILE_UPDATE(AuditCategory.SECURITY, AuditSeverity.INFO, "User profile updated"),

    // =========================================================================
    // EVENT_MANAGEMENT - Event Lifecycle
    // =========================================================================
    EVENT_CREATE(AuditCategory.EVENT_MANAGEMENT, AuditSeverity.IMPORTANT, "Event created: {eventName}"),
    EVENT_UPDATE(AuditCategory.EVENT_MANAGEMENT, AuditSeverity.IMPORTANT, "Event updated: {eventName}"),
    EVENT_DELETE(AuditCategory.EVENT_MANAGEMENT, AuditSeverity.CRITICAL, "Event deleted: {eventName}"),
    EVENT_STATUS_CHANGE(AuditCategory.EVENT_MANAGEMENT, AuditSeverity.IMPORTANT, "Event status changed to {status}"),
    EVENT_PRICE_TEMPLATE_CREATE(AuditCategory.EVENT_MANAGEMENT, AuditSeverity.IMPORTANT, "Price template created"),
    EVENT_PRICE_TEMPLATE_UPDATE(AuditCategory.EVENT_MANAGEMENT, AuditSeverity.IMPORTANT, "Price template updated"),
    EVENT_PRICE_TEMPLATE_DELETE(AuditCategory.EVENT_MANAGEMENT, AuditSeverity.IMPORTANT, "Price template deleted"),
    EVENT_SESSION_PRICING_ASSIGN(
        AuditCategory.EVENT_MANAGEMENT,
        AuditSeverity.IMPORTANT,
        "Pricing assigned to session"
    ),
    EVENT_PRICING_ASSIGN(AuditCategory.EVENT_MANAGEMENT, AuditSeverity.IMPORTANT, "Pricing assigned to event"),
    EVENT_SESSION_STATUS_CHANGE(AuditCategory.EVENT_MANAGEMENT, AuditSeverity.IMPORTANT, "Session status changed"),
    EVENT_SEATS_CLOSE(AuditCategory.EVENT_MANAGEMENT, AuditSeverity.IMPORTANT, "Seats closed for sale"),
    EVENT_SEATS_OPEN(AuditCategory.EVENT_MANAGEMENT, AuditSeverity.IMPORTANT, "Seats opened for sale"),
    EVENT_TABLES_CLOSE(AuditCategory.EVENT_MANAGEMENT, AuditSeverity.IMPORTANT, "Tables closed for sale"),
    EVENT_TABLES_OPEN(AuditCategory.EVENT_MANAGEMENT, AuditSeverity.IMPORTANT, "Tables opened for sale"),

    // =========================================================================
    // SALES - Ticket Sales & Cart Operations
    // =========================================================================
    BOOKING_DIRECT_SALE(AuditCategory.SALES, AuditSeverity.IMPORTANT, "Direct ticket sale completed"),
    BOOKING_INVALIDATED(AuditCategory.SALES, AuditSeverity.CRITICAL, "Booking invalidated"),
    TICKET_SCAN(AuditCategory.SALES, AuditSeverity.INFO, "Ticket scanned"),
    TICKET_INVALIDATE_BOOKING(AuditCategory.SALES, AuditSeverity.CRITICAL, "Booking tickets invalidated"),
    TICKET_INVALIDATE_ALL(AuditCategory.SALES, AuditSeverity.CRITICAL, "All tickets for booking invalidated"),
    TICKET_INVALIDATE_ITEM(AuditCategory.SALES, AuditSeverity.CRITICAL, "Ticket item invalidated"),
    SCANNER_SESSION_CREATE(AuditCategory.SALES, AuditSeverity.INFO, "Scanner session created"),
    STAFF_CART_ADD_SEAT(AuditCategory.SALES, AuditSeverity.INFO, "Seat added to cart"),
    STAFF_CART_ADD_GA(AuditCategory.SALES, AuditSeverity.INFO, "GA tickets added to cart"),
    STAFF_CART_ADD_TABLE(AuditCategory.SALES, AuditSeverity.INFO, "Table added to cart"),
    STAFF_CART_REMOVE_SEAT(AuditCategory.SALES, AuditSeverity.INFO, "Seat removed from cart"),
    STAFF_CART_UPDATE_GA(AuditCategory.SALES, AuditSeverity.INFO, "GA quantity updated in cart"),
    STAFF_CART_REMOVE_GA(AuditCategory.SALES, AuditSeverity.INFO, "GA tickets removed from cart"),
    STAFF_CART_REMOVE_TABLE(AuditCategory.SALES, AuditSeverity.INFO, "Table removed from cart"),
    STAFF_CART_CLEARED(AuditCategory.SALES, AuditSeverity.INFO, "Cart cleared"),

    // =========================================================================
    // CONFIGURATION - Venue & System Settings
    // =========================================================================
    VENUE_CREATE(AuditCategory.CONFIGURATION, AuditSeverity.CRITICAL, "Venue created: {venueName}"),
    VENUE_UPDATE(AuditCategory.CONFIGURATION, AuditSeverity.IMPORTANT, "Venue updated: {venueName}"),
    VENUE_DELETE(AuditCategory.CONFIGURATION, AuditSeverity.CRITICAL, "Venue deleted"),
    VENUE_ACTIVATE(AuditCategory.CONFIGURATION, AuditSeverity.IMPORTANT, "Venue activated"),
    VENUE_SUSPEND(AuditCategory.CONFIGURATION, AuditSeverity.CRITICAL, "Venue suspended"),
    VENUE_SMTP_UPDATED(AuditCategory.CONFIGURATION, AuditSeverity.IMPORTANT, "Venue SMTP settings updated"),
    VENUE_SMTP_DELETED(AuditCategory.CONFIGURATION, AuditSeverity.IMPORTANT, "Venue SMTP settings deleted"),
    VENUE_BRANDING_UPDATED(AuditCategory.CONFIGURATION, AuditSeverity.INFO, "Venue branding updated"),
    VENUE_PROMO_CREATE(AuditCategory.CONFIGURATION, AuditSeverity.IMPORTANT, "Promo code created: {promoCode}"),
    VENUE_PROMO_UPDATE(AuditCategory.CONFIGURATION, AuditSeverity.IMPORTANT, "Promo code updated: {promoCode}"),
    VENUE_PROMO_DEACTIVATE(AuditCategory.CONFIGURATION, AuditSeverity.IMPORTANT, "Promo code deactivated: {promoCode}"),
    SEATING_CHART_CREATE(AuditCategory.CONFIGURATION, AuditSeverity.IMPORTANT, "Seating chart created"),
    SEATING_CHART_UPDATE(AuditCategory.CONFIGURATION, AuditSeverity.IMPORTANT, "Seating chart updated"),
    SEATING_CHART_REPLACE_LAYOUT(AuditCategory.CONFIGURATION, AuditSeverity.IMPORTANT, "Seating chart layout replaced"),
    SEATING_CHART_CLONE(AuditCategory.CONFIGURATION, AuditSeverity.INFO, "Seating chart cloned"),
    SEATING_CHART_DELETE(AuditCategory.CONFIGURATION, AuditSeverity.CRITICAL, "Seating chart deleted"),
    SEATING_DEFAULT_CATEGORY_UPDATE(
        AuditCategory.CONFIGURATION,
        AuditSeverity.IMPORTANT,
        "Default seating category updated"
    ),
    SEATING_SELECTED_CATEGORY_UPDATE(
        AuditCategory.CONFIGURATION,
        AuditSeverity.IMPORTANT,
        "Selected seating category updated"
    ),
    SEATING_VISUALS_UPDATE(AuditCategory.CONFIGURATION, AuditSeverity.INFO, "Seating visuals updated"),
    ORGANIZATION_CREATE(AuditCategory.CONFIGURATION, AuditSeverity.CRITICAL, "Organization created"),
    ORGANIZATION_UPDATE(AuditCategory.CONFIGURATION, AuditSeverity.IMPORTANT, "Organization updated"),
    LOCATION_REGION_CREATE(AuditCategory.CONFIGURATION, AuditSeverity.IMPORTANT, "Region created"),
    LOCATION_REGION_UPDATE(AuditCategory.CONFIGURATION, AuditSeverity.IMPORTANT, "Region updated"),
    LOCATION_CITY_CREATE(AuditCategory.CONFIGURATION, AuditSeverity.IMPORTANT, "City created"),
    LOCATION_CITY_UPDATE(AuditCategory.CONFIGURATION, AuditSeverity.IMPORTANT, "City updated"),

    // =========================================================================
    // PLATFORM - External API Operations
    // =========================================================================
    PLATFORM_CREATE(AuditCategory.PLATFORM, AuditSeverity.CRITICAL, "Platform created"),
    PLATFORM_UPDATE(AuditCategory.PLATFORM, AuditSeverity.IMPORTANT, "Platform updated"),
    PLATFORM_DELETE(AuditCategory.PLATFORM, AuditSeverity.CRITICAL, "Platform deleted"),
    PLATFORM_REGENERATE_SECRET(AuditCategory.PLATFORM, AuditSeverity.CRITICAL, "Platform secret regenerated"),
    PLATFORM_CLEAR_NONCES(AuditCategory.PLATFORM, AuditSeverity.IMPORTANT, "Platform nonces cleared"),
    PLATFORM_WEBHOOK_REPLAY(AuditCategory.PLATFORM, AuditSeverity.INFO, "Webhook replayed"),
    PLATFORM_SUBSCRIBE_EVENT(AuditCategory.PLATFORM, AuditSeverity.INFO, "Platform subscribed to event"),
    PLATFORM_BULK_SUBSCRIBE(AuditCategory.PLATFORM, AuditSeverity.INFO, "Platform bulk subscribed"),
    PLATFORM_UNSUBSCRIBE_EVENT(AuditCategory.PLATFORM, AuditSeverity.INFO, "Platform unsubscribed from event"),
    PLATFORM_HOLD(AuditCategory.PLATFORM, AuditSeverity.INFO, "Platform hold created"),
    PLATFORM_HOLD_SIMPLE(AuditCategory.PLATFORM, AuditSeverity.INFO, "Platform simple hold created"),
    PLATFORM_CHECKOUT(AuditCategory.PLATFORM, AuditSeverity.IMPORTANT, "Platform checkout completed"),
    PLATFORM_CONFIRM(AuditCategory.PLATFORM, AuditSeverity.IMPORTANT, "Platform booking confirmed"),
    PLATFORM_RELEASE(AuditCategory.PLATFORM, AuditSeverity.INFO, "Platform hold released"),
    PLATFORM_DIRECT_BOOKING(AuditCategory.PLATFORM, AuditSeverity.IMPORTANT, "Platform direct booking"),
    PLATFORM_EASY_RESERVE(AuditCategory.PLATFORM, AuditSeverity.INFO, "Platform easy reserve"),
    PLATFORM_EASY_CONFIRM(AuditCategory.PLATFORM, AuditSeverity.IMPORTANT, "Platform easy confirm"),
    PLATFORM_EASY_RELEASE(AuditCategory.PLATFORM, AuditSeverity.INFO, "Platform easy release"),
    PLATFORM_ADV_HOLD(AuditCategory.PLATFORM, AuditSeverity.INFO, "Platform advanced hold"),
    PLATFORM_ADV_HOLD_SIMPLE(AuditCategory.PLATFORM, AuditSeverity.INFO, "Platform advanced simple hold"),
    PLATFORM_ADV_CHECKOUT(AuditCategory.PLATFORM, AuditSeverity.IMPORTANT, "Platform advanced checkout"),
    PLATFORM_ADV_CONFIRM(AuditCategory.PLATFORM, AuditSeverity.IMPORTANT, "Platform advanced confirm"),
    PLATFORM_ADV_RELEASE(AuditCategory.PLATFORM, AuditSeverity.INFO, "Platform advanced release"),
    PLATFORM_ADV_DIRECT_BOOKING(AuditCategory.PLATFORM, AuditSeverity.IMPORTANT, "Platform advanced direct booking"),

    // =========================================================================
    // MEDIA - File Operations
    // =========================================================================
    MEDIA_UPLOAD_SINGLE(AuditCategory.MEDIA, AuditSeverity.INFO, "Single file uploaded"),
    MEDIA_UPLOAD_BATCH(AuditCategory.MEDIA, AuditSeverity.INFO, "Batch files uploaded"),
    MEDIA_DELETE(AuditCategory.MEDIA, AuditSeverity.IMPORTANT, "Media file deleted"),

    // =========================================================================
    // SYSTEM - Test & Utility Operations
    // =========================================================================
    TEST_EMAIL_GLOBAL(AuditCategory.SYSTEM, AuditSeverity.INFO, "Test email sent (global)"),
    TEST_EMAIL_VENUE(AuditCategory.SYSTEM, AuditSeverity.INFO, "Test email sent (venue)"),
    TEST_EMAIL_PREVIEW(AuditCategory.SYSTEM, AuditSeverity.INFO, "Email preview generated"),
    TEST_EMAIL_SEND_BOOKING(AuditCategory.SYSTEM, AuditSeverity.INFO, "Test booking email sent"),

    // Fallback for unknown actions
    UNKNOWN(AuditCategory.SYSTEM, AuditSeverity.INFO, "Unknown action performed");

    companion object {
        /**
         * Resolves an action string to the enum, returning UNKNOWN if not found.
         * Handles legacy action names with underscores consistently.
         */
        fun fromString(action: String): AuditAction {
            return try {
                valueOf(action.uppercase().replace("-", "_"))
            } catch (e: IllegalArgumentException) {
                UNKNOWN
            }
        }
    }
}
