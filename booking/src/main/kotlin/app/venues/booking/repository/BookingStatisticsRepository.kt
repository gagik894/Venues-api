package app.venues.booking.repository

import app.venues.booking.domain.Booking
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.*

@Repository
interface BookingStatisticsRepository : JpaRepository<Booking, UUID> {

    @Query(
        """
        SELECT b.sessionId AS sessionId,
               SUM(bi.quantity) AS ticketsSold,
               SUM(bi.unitPrice * bi.quantity) AS totalRevenue
        FROM BookingItem bi
        JOIN bi.booking b
        WHERE b.sessionId IN :sessionIds
          AND b.status = 'CONFIRMED'
        GROUP BY b.sessionId
        """
    )
    fun aggregateSessionSales(sessionIds: List<UUID>): List<SessionSalesProjection>

    @Query(
        """
        SELECT COALESCE(bi.priceTemplateName, 'UNASSIGNED') AS templateName,
               SUM(bi.quantity) AS soldTickets,
               SUM(bi.unitPrice * bi.quantity) AS totalRevenue
        FROM BookingItem bi
        JOIN bi.booking b
        WHERE b.sessionId IN :sessionIds
          AND b.status = 'CONFIRMED'
        GROUP BY COALESCE(bi.priceTemplateName, 'UNASSIGNED')
        """
    )
    fun aggregateTemplateSales(sessionIds: List<UUID>): List<TemplateSalesProjection>

    @Query(
        """
        SELECT COALESCE(b.promoCode, 'NONE') AS promoCode,
               SUM(bi.quantity) AS soldTickets,
               SUM(bi.unitPrice * bi.quantity) AS totalRevenue,
               SUM(b.discountAmount) AS totalPromoLoss
        FROM BookingItem bi
        JOIN bi.booking b
        WHERE b.sessionId IN :sessionIds
          AND b.status = 'CONFIRMED'
        GROUP BY COALESCE(b.promoCode, 'NONE')
        """
    )
    fun aggregatePromoStats(sessionIds: List<UUID>): List<PromoCodeStatsProjection>

    @Query(
        """
        SELECT b.salesChannel AS salesChannel,
               b.platformId AS platformId,
               SUM(bi.quantity) AS soldTickets,
               SUM(bi.unitPrice * bi.quantity) AS totalRevenue,
               SUM(b.discountAmount) AS totalPromoLoss
        FROM BookingItem bi
        JOIN bi.booking b
        WHERE b.sessionId IN :sessionIds
          AND b.status = 'CONFIRMED'
        GROUP BY b.salesChannel, b.platformId
        """
    )
    fun aggregatePlatformStats(sessionIds: List<UUID>): List<PlatformStatsProjection>

    @Query(
        """
        SELECT DATE(b.confirmedAt) AS bookingDate,
               SUM(bi.quantity) AS soldTickets,
               SUM(bi.unitPrice * bi.quantity) AS totalRevenue,
               SUM(b.discountAmount) AS totalPromoLoss
        FROM BookingItem bi
        JOIN bi.booking b
        WHERE b.sessionId IN :sessionIds
          AND b.status = 'CONFIRMED'
          AND b.confirmedAt IS NOT NULL
        GROUP BY DATE(b.confirmedAt)
        ORDER BY DATE(b.confirmedAt)
        """
    )
    fun aggregateDayStats(sessionIds: List<UUID>): List<DayStatsProjection>

    @Query(
        """
        SELECT bi.seatId AS seatId,
               COALESCE(bi.priceTemplateName, 'UNASSIGNED') AS templateName,
               SUM(bi.quantity) AS soldTickets,
               SUM(bi.unitPrice * bi.quantity) AS totalRevenue
        FROM BookingItem bi
        JOIN bi.booking b
        WHERE b.sessionId IN :sessionIds
          AND b.status = 'CONFIRMED'
          AND bi.seatId IS NOT NULL
        GROUP BY bi.seatId, COALESCE(bi.priceTemplateName, 'UNASSIGNED')
        """
    )
    fun aggregateSeatSales(sessionIds: List<UUID>): List<SeatSalesProjection>

    @Query(
        """
        SELECT bi.gaAreaId AS gaAreaId,
               COALESCE(bi.priceTemplateName, 'UNASSIGNED') AS templateName,
               SUM(bi.quantity) AS soldTickets,
               SUM(bi.unitPrice * bi.quantity) AS totalRevenue
        FROM BookingItem bi
        JOIN bi.booking b
        WHERE b.sessionId IN :sessionIds
          AND b.status = 'CONFIRMED'
          AND bi.gaAreaId IS NOT NULL
        GROUP BY bi.gaAreaId, COALESCE(bi.priceTemplateName, 'UNASSIGNED')
        """
    )
    fun aggregateGaSales(sessionIds: List<UUID>): List<GaSalesProjection>

    @Query(
        """
        SELECT bi.tableId AS tableId,
               COALESCE(bi.priceTemplateName, 'UNASSIGNED') AS templateName,
               SUM(bi.quantity) AS soldTickets,
               SUM(bi.unitPrice * bi.quantity) AS totalRevenue
        FROM BookingItem bi
        JOIN bi.booking b
        WHERE b.sessionId IN :sessionIds
          AND b.status = 'CONFIRMED'
          AND bi.tableId IS NOT NULL
        GROUP BY bi.tableId, COALESCE(bi.priceTemplateName, 'UNASSIGNED')
        """
    )
    fun aggregateTableSales(sessionIds: List<UUID>): List<TableSalesProjection>

    @Query(
        """
                SELECT COUNT(DISTINCT b.id) AS orders,
                             COALESCE(SUM(b.totalPrice), 0) AS revenue,
                             COALESCE(SUM(bi.quantity), 0) AS ticketsSold
                FROM BookingItem bi
                JOIN bi.booking b
                WHERE b.venueId = :venueId
                    AND b.status = 'CONFIRMED'
                    AND b.confirmedAt IS NOT NULL
                    AND b.confirmedAt >= :startInclusive
                    AND b.confirmedAt < :endExclusive
                """
    )
    fun aggregateVenueOverview(
        venueId: UUID,
        startInclusive: Instant,
        endExclusive: Instant
    ): VenueOverviewProjection?

    @Query(
        """
                SELECT function('date', b.confirmedAt) AS bookingDate,
                             COUNT(DISTINCT b.id) AS orders,
                             COALESCE(SUM(b.totalPrice), 0) AS revenue,
                             COALESCE(SUM(bi.quantity), 0) AS ticketsSold
                FROM BookingItem bi
                JOIN bi.booking b
                WHERE b.venueId = :venueId
                    AND b.status = 'CONFIRMED'
                    AND b.confirmedAt IS NOT NULL
                    AND b.confirmedAt >= :startInclusive
                    AND b.confirmedAt < :endExclusive
                GROUP BY function('date', b.confirmedAt)
                ORDER BY function('date', b.confirmedAt)
                """
    )
    fun aggregateVenueByDate(
        venueId: UUID,
        startInclusive: Instant,
        endExclusive: Instant
    ): List<VenueDateProjection>

    @Query(
        """
                SELECT b.salesChannel AS salesChannel,
                             b.platformId AS platformId,
                             COUNT(DISTINCT b.id) AS orders,
                             COALESCE(SUM(b.totalPrice), 0) AS revenue,
                             COALESCE(SUM(bi.quantity), 0) AS ticketsSold
                FROM BookingItem bi
                JOIN bi.booking b
                WHERE b.venueId = :venueId
                    AND b.status = 'CONFIRMED'
                    AND b.confirmedAt IS NOT NULL
                    AND b.confirmedAt >= :startInclusive
                    AND b.confirmedAt < :endExclusive
                GROUP BY b.salesChannel, b.platformId
                """
    )
    fun aggregateVenueByPlatform(
        venueId: UUID,
        startInclusive: Instant,
        endExclusive: Instant
    ): List<VenuePlatformProjection>

    @Query(
        """
        SELECT DISTINCT b.currency
        FROM Booking b
        WHERE b.venueId = :venueId
          AND b.status = 'CONFIRMED'
          AND b.confirmedAt IS NOT NULL
          AND b.confirmedAt >= :startInclusive
          AND b.confirmedAt < :endExclusive
        """
    )
    fun findVenueCurrencies(
        venueId: UUID,
        startInclusive: Instant,
        endExclusive: Instant
    ): List<String>

    @Query(
        value = """
            SELECT b.currency
            FROM bookings b
            WHERE b.venue_id = :venueId
              AND b.status = 'CONFIRMED'
              AND b.confirmed_at IS NOT NULL
            ORDER BY b.confirmed_at DESC
            LIMIT 1
        """,
        nativeQuery = true
    )
    fun findMostRecentVenueCurrency(venueId: UUID): String?
}

interface SessionSalesProjection {
    fun getSessionId(): UUID
    fun getTicketsSold(): Long
    fun getTotalRevenue(): BigDecimal
}

interface TemplateSalesProjection {
    fun getTemplateName(): String
    fun getSoldTickets(): Long
    fun getTotalRevenue(): BigDecimal
}

interface PromoCodeStatsProjection {
    fun getPromoCode(): String
    fun getSoldTickets(): Long
    fun getTotalRevenue(): BigDecimal
    fun getTotalPromoLoss(): BigDecimal
}

interface PlatformStatsProjection {
    fun getSalesChannel(): String
    fun getPlatformId(): UUID?
    fun getSoldTickets(): Long
    fun getTotalRevenue(): BigDecimal
    fun getTotalPromoLoss(): BigDecimal
}

interface DayStatsProjection {
    fun getBookingDate(): LocalDate
    fun getSoldTickets(): Long
    fun getTotalRevenue(): BigDecimal
    fun getTotalPromoLoss(): BigDecimal
}

interface SeatSalesProjection {
    fun getSeatId(): Long
    fun getTemplateName(): String
    fun getSoldTickets(): Long
    fun getTotalRevenue(): BigDecimal
}

interface GaSalesProjection {
    fun getGaAreaId(): Long
    fun getTemplateName(): String
    fun getSoldTickets(): Long
    fun getTotalRevenue(): BigDecimal
}

interface TableSalesProjection {
    fun getTableId(): Long
    fun getTemplateName(): String
    fun getSoldTickets(): Long
    fun getTotalRevenue(): BigDecimal
}

interface VenueOverviewProjection {
    fun getOrders(): Long
    fun getRevenue(): BigDecimal
    fun getTicketsSold(): Long
}

interface VenueDateProjection {
    fun getBookingDate(): LocalDate
    fun getOrders(): Long
    fun getRevenue(): BigDecimal
    fun getTicketsSold(): Long
}

interface VenuePlatformProjection {
    fun getSalesChannel(): String
    fun getPlatformId(): UUID?
    fun getOrders(): Long
    fun getRevenue(): BigDecimal
    fun getTicketsSold(): Long
}
