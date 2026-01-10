package app.venues.event.repository

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import java.util.*
import org.springframework.stereotype.Repository as SpringRepository

@SpringRepository
interface CodeReservationRepository : Repository<Any, Long> {

    /**
     * Atomically reserve a seat (AVAILABLE -> RESERVED) by seat code scoped to the session.
     * Returns resolved seatId + unit price, or empty list if unavailable/not priced.
     *
     * Uses type-safe projection to avoid unsafe casting and ensure compile-time safety.
     * Schema-qualified table names ensure robustness across deployment environments.
     */
    @Query(
        nativeQuery = true,
        value = """
        WITH updated AS (
            UPDATE public.session_seat_configs ssc
            SET status = 'RESERVED'
            FROM public.chart_seats cs
            WHERE ssc.session_id = :sessionId
              AND ssc.seat_id = cs.id
              AND cs.code = :seatCode
              AND ssc.status = 'AVAILABLE'
            RETURNING ssc.seat_id AS seat_id, ssc.price_template_id AS price_template_id
        )
        SELECT u.seat_id AS seatId, pt.price AS price
        FROM updated u
        JOIN public.event_price_templates pt ON pt.id = u.price_template_id
        """
    )
    fun reserveSeatByCodeAndGetPrice(sessionId: UUID, seatCode: String): List<SeatReservationProjection>

    /**
     * Atomically reserve GA tickets by GA code scoped to the session.
     *
     * Uses type-safe projection to avoid unsafe casting and ensure compile-time safety.
     * Schema-qualified table names ensure robustness across deployment environments.
     */
    @Query(
        nativeQuery = true,
        value = """
        WITH updated AS (
            UPDATE public.session_level_configs slc
            SET sold_count = sold_count + :quantity
            FROM public.chart_ga_areas ga
            WHERE slc.session_id = :sessionId
              AND slc.ga_area_id = ga.id
              AND ga.code = :gaCode
              AND slc.status = 'AVAILABLE'
              AND (slc.capacity - slc.sold_count) >= :quantity
            RETURNING slc.ga_area_id AS ga_area_id, slc.price_template_id AS price_template_id
        )
        SELECT u.ga_area_id AS gaAreaId, pt.price AS price
        FROM updated u
        JOIN public.event_price_templates pt ON pt.id = u.price_template_id
        """
    )
    fun reserveGaByCodeAndGetPrice(sessionId: UUID, gaCode: String, quantity: Int): List<GaReservationProjection>

    /**
     * Atomically reserve a table by code scoped to the session and return resolved tableId + unit price.
     *
     * Uses type-safe projection to avoid unsafe casting and ensure compile-time safety.
     * Schema-qualified table names ensure robustness across deployment environments.
     */
    @Query(
        nativeQuery = true,
        value = """
        WITH updated AS (
            UPDATE public.session_table_configs stc
            SET status = 'RESERVED'
            FROM public.chart_tables ct
            WHERE stc.session_id = :sessionId
              AND stc.table_id = ct.id
              AND ct.code = :tableCode
              AND stc.status = 'AVAILABLE'
            RETURNING stc.table_id AS table_id, stc.price_template_id AS price_template_id
        )
        SELECT u.table_id AS tableId, pt.price AS price
        FROM updated u
        JOIN public.event_price_templates pt ON pt.id = u.price_template_id
        """
    )
    fun reserveTableByCodeAndGetPrice(sessionId: UUID, tableCode: String): List<TableReservationProjection>
}
