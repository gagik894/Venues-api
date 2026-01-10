package app.venues.event.repository

import java.math.BigDecimal

/**
 * Type-safe projection for seat reservation query results.
 * Avoids unsafe casting and provides compile-time safety.
 */
interface SeatReservationProjection {
    fun getSeatId(): Long
    fun getPrice(): BigDecimal
}

/**
 * Type-safe projection for GA area reservation query results.
 * Avoids unsafe casting and provides compile-time safety.
 */
interface GaReservationProjection {
    fun getGaAreaId(): Long
    fun getPrice(): BigDecimal
}

/**
 * Type-safe projection for table reservation query results.
 * Avoids unsafe casting and provides compile-time safety.
 */
interface TableReservationProjection {
    fun getTableId(): Long
    fun getPrice(): BigDecimal
}

