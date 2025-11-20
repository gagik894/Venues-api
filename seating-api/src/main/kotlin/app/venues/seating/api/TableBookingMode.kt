package app.venues.seating.api

/**
 * Defines how a table can be booked.
 *
 * Controls whether customers can book individual seats,
 * the entire table, or have a choice between both options.
 */
enum class TableBookingMode {
    /**
     * Only individual seats can be booked.
     * Table as a unit is not available for purchase.
     */
    SEATS_ONLY,

    /**
     * Only the entire table can be booked.
     * Individual seats cannot be purchased separately.
     */
    TABLE_ONLY,

    /**
     * Customer can choose to book individual seats OR the entire table.
     * Once any seat is reserved/sold, table booking becomes unavailable.
     * Once table is reserved/sold, individual seat booking becomes unavailable.
     */
    FLEXIBLE
}
