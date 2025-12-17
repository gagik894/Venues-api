package app.venues.booking.domain

/**
 * Enum representing the sales channel through which a booking was created.
 *
 * This allows tracking and analytics on where bookings originate:
 * - Customer self-service through website
 * - Staff-assisted in-person sales
 * - External platform API integrations
 */
enum class SalesChannel {
    /**
     * Customer purchase through the venue's website or mobile app.
     * Self-service online booking.
     */
    WEBSITE,

    /**
     * In-person sale made by venue staff.
     * Created via DirectSalesService or staff cart checkout.
     * Should have staffId set to track which staff member made the sale.
     */
    DIRECT_SALE,

    /**
     * Sale made through a 3rd party platform integration.
     * Created via platform API with platformId referencing the external platform.
     * Used for integrations with ticketing platforms like Ticketmaster, StubHub, etc.
     */
    PLATFORM
}
