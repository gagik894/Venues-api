package app.venues.event.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.*

/**
 * Unit tests for EventSession domain entity status transitions.
 *
 * Tests the state machine behavior and validation rules.
 */
class EventSessionTest {

    @Test
    fun `pauseSales - should transition from ON_SALE to PAUSED`() {
        // Given
        val session = createTestSession()
        assertEquals(SessionStatus.ON_SALE, session.status)

        // When
        session.pauseSales()

        // Then
        assertEquals(SessionStatus.PAUSED, session.status)
    }

    @Test
    fun `pauseSales - should throw error when not in ON_SALE status`() {
        // Given
        val session = createTestSession()
        setSessionStatus(session, SessionStatus.PAUSED)

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            session.pauseSales()
        }
        assertTrue(exception.message!!.contains("must be ON_SALE"))
    }

    @Test
    fun `resumeSales - should transition from PAUSED to ON_SALE`() {
        // Given
        val session = createTestSession()
        setSessionStatus(session, SessionStatus.PAUSED)

        // When
        session.resumeSales()

        // Then
        assertEquals(SessionStatus.ON_SALE, session.status)
    }

    @Test
    fun `resumeSales - should throw error when not in PAUSED status`() {
        // Given
        val session = createTestSession()
        assertEquals(SessionStatus.ON_SALE, session.status)

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            session.resumeSales()
        }
        assertTrue(exception.message!!.contains("must be PAUSED"))
    }

    @Test
    fun `markSoldOut - should transition from ON_SALE to SOLD_OUT`() {
        // Given
        val session = createTestSession()
        assertEquals(SessionStatus.ON_SALE, session.status)

        // When
        session.markSoldOut()

        // Then
        assertEquals(SessionStatus.SOLD_OUT, session.status)
    }

    @Test
    fun `markSoldOut - should transition from PAUSED to SOLD_OUT`() {
        // Given
        val session = createTestSession()
        setSessionStatus(session, SessionStatus.PAUSED)

        // When
        session.markSoldOut()

        // Then
        assertEquals(SessionStatus.SOLD_OUT, session.status)
    }

    @Test
    fun `markSoldOut - should throw error when not in ON_SALE or PAUSED`() {
        // Given
        val session = createTestSession()
        setSessionStatus(session, SessionStatus.SOLD_OUT)

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            session.markSoldOut()
        }
        assertTrue(exception.message!!.contains("must be ON_SALE or PAUSED"))
    }

    @Test
    fun `closeSales - should transition from ON_SALE to SALES_CLOSED`() {
        // Given
        val session = createTestSession()
        assertEquals(SessionStatus.ON_SALE, session.status)

        // When
        session.closeSales()

        // Then
        assertEquals(SessionStatus.SALES_CLOSED, session.status)
    }

    @Test
    fun `closeSales - should transition from PAUSED to SALES_CLOSED`() {
        // Given
        val session = createTestSession()
        setSessionStatus(session, SessionStatus.PAUSED)

        // When
        session.closeSales()

        // Then
        assertEquals(SessionStatus.SALES_CLOSED, session.status)
    }

    @Test
    fun `closeSales - should transition from SOLD_OUT to SALES_CLOSED`() {
        // Given
        val session = createTestSession()
        setSessionStatus(session, SessionStatus.SOLD_OUT)

        // When
        session.closeSales()

        // Then
        assertEquals(SessionStatus.SALES_CLOSED, session.status)
    }

    @Test
    fun `closeSales - should throw error when already SALES_CLOSED`() {
        // Given
        val session = createTestSession()
        setSessionStatus(session, SessionStatus.SALES_CLOSED)

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            session.closeSales()
        }
        assertTrue(exception.message!!.contains("already closed"))
    }

    @Test
    fun `closeSales - should throw error when CANCELLED`() {
        // Given
        val session = createTestSession()
        setSessionStatus(session, SessionStatus.CANCELLED)

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            session.closeSales()
        }
        assertTrue(exception.message!!.contains("cancelled"))
    }

    @Test
    fun `cancel - should transition from any status except CANCELLED`() {
        // Test from ON_SALE
        val session1 = createTestSession()
        session1.cancel()
        assertEquals(SessionStatus.CANCELLED, session1.status)

        // Test from PAUSED
        val session2 = createTestSession()
        setSessionStatus(session2, SessionStatus.PAUSED)
        session2.cancel()
        assertEquals(SessionStatus.CANCELLED, session2.status)

        // Test from SOLD_OUT
        val session3 = createTestSession()
        setSessionStatus(session3, SessionStatus.SOLD_OUT)
        session3.cancel()
        assertEquals(SessionStatus.CANCELLED, session3.status)

        // Test from SALES_CLOSED
        val session4 = createTestSession()
        setSessionStatus(session4, SessionStatus.SALES_CLOSED)
        session4.cancel()
        assertEquals(SessionStatus.CANCELLED, session4.status)
    }

    @Test
    fun `cancel - should throw error when already CANCELLED`() {
        // Given
        val session = createTestSession()
        setSessionStatus(session, SessionStatus.CANCELLED)

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            session.cancel()
        }
        assertTrue(exception.message!!.contains("already cancelled"))
    }

    @Test
    fun `canTransitionTo - should return correct values for ON_SALE status`() {
        val session = createTestSession()
        assertEquals(SessionStatus.ON_SALE, session.status)

        assertTrue(session.canTransitionTo(SessionStatus.PAUSED))
        assertTrue(session.canTransitionTo(SessionStatus.SOLD_OUT))
        assertTrue(session.canTransitionTo(SessionStatus.SALES_CLOSED))
        assertTrue(session.canTransitionTo(SessionStatus.CANCELLED))
        assertFalse(session.canTransitionTo(SessionStatus.ON_SALE)) // Same status
    }

    @Test
    fun `canTransitionTo - should return correct values for PAUSED status`() {
        val session = createTestSession()
        setSessionStatus(session, SessionStatus.PAUSED)

        assertTrue(session.canTransitionTo(SessionStatus.ON_SALE))
        assertTrue(session.canTransitionTo(SessionStatus.SOLD_OUT))
        assertTrue(session.canTransitionTo(SessionStatus.SALES_CLOSED))
        assertTrue(session.canTransitionTo(SessionStatus.CANCELLED))
        assertFalse(session.canTransitionTo(SessionStatus.PAUSED)) // Same status
    }

    @Test
    fun `canTransitionTo - should return correct values for SOLD_OUT status`() {
        val session = createTestSession()
        setSessionStatus(session, SessionStatus.SOLD_OUT)

        assertTrue(session.canTransitionTo(SessionStatus.SALES_CLOSED))
        assertTrue(session.canTransitionTo(SessionStatus.CANCELLED))
        assertFalse(session.canTransitionTo(SessionStatus.ON_SALE))
        assertFalse(session.canTransitionTo(SessionStatus.PAUSED))
        assertFalse(session.canTransitionTo(SessionStatus.SOLD_OUT)) // Same status
    }

    @Test
    fun `canTransitionTo - should return correct values for SALES_CLOSED status`() {
        val session = createTestSession()
        setSessionStatus(session, SessionStatus.SALES_CLOSED)

        assertTrue(session.canTransitionTo(SessionStatus.CANCELLED))
        assertFalse(session.canTransitionTo(SessionStatus.ON_SALE))
        assertFalse(session.canTransitionTo(SessionStatus.PAUSED))
        assertFalse(session.canTransitionTo(SessionStatus.SOLD_OUT))
        assertFalse(session.canTransitionTo(SessionStatus.SALES_CLOSED)) // Same status
    }

    @Test
    fun `canTransitionTo - should return false for all transitions from CANCELLED`() {
        val session = createTestSession()
        setSessionStatus(session, SessionStatus.CANCELLED)

        SessionStatus.entries.forEach { targetStatus ->
            assertFalse(session.canTransitionTo(targetStatus))
        }
    }

    @Test
    fun `transitionTo - should execute valid transitions correctly`() {
        // ON_SALE → PAUSED
        val session1 = createTestSession()
        session1.transitionTo(SessionStatus.PAUSED)
        assertEquals(SessionStatus.PAUSED, session1.status)

        // PAUSED → ON_SALE
        val session2 = createTestSession()
        setSessionStatus(session2, SessionStatus.PAUSED)
        session2.transitionTo(SessionStatus.ON_SALE)
        assertEquals(SessionStatus.ON_SALE, session2.status)

        // ON_SALE → SOLD_OUT
        val session3 = createTestSession()
        session3.transitionTo(SessionStatus.SOLD_OUT)
        assertEquals(SessionStatus.SOLD_OUT, session3.status)

        // ON_SALE → SALES_CLOSED
        val session4 = createTestSession()
        session4.transitionTo(SessionStatus.SALES_CLOSED)
        assertEquals(SessionStatus.SALES_CLOSED, session4.status)

        // Any → CANCELLED
        val session5 = createTestSession()
        session5.transitionTo(SessionStatus.CANCELLED)
        assertEquals(SessionStatus.CANCELLED, session5.status)
    }

    @Test
    fun `transitionTo - should throw error for invalid transitions`() {
        // SOLD_OUT → ON_SALE (not allowed)
        val session1 = createTestSession()
        setSessionStatus(session1, SessionStatus.SOLD_OUT)
        assertThrows<IllegalArgumentException> {
            session1.transitionTo(SessionStatus.ON_SALE)
        }

        // SOLD_OUT → PAUSED (not allowed)
        val session2 = createTestSession()
        setSessionStatus(session2, SessionStatus.SOLD_OUT)
        assertThrows<IllegalArgumentException> {
            session2.transitionTo(SessionStatus.PAUSED)
        }

        // SALES_CLOSED → ON_SALE (not allowed)
        val session3 = createTestSession()
        setSessionStatus(session3, SessionStatus.SALES_CLOSED)
        assertThrows<IllegalArgumentException> {
            session3.transitionTo(SessionStatus.ON_SALE)
        }

        // CANCELLED → Any (not allowed)
        val session4 = createTestSession()
        setSessionStatus(session4, SessionStatus.CANCELLED)
        assertThrows<IllegalArgumentException> {
            session4.transitionTo(SessionStatus.ON_SALE)
        }
    }

    @Test
    fun `isBookable - should return true for ON_SALE with available tickets and future start time`() {
        val session = createTestSession()
        assertTrue(session.isBookable())
    }

    @Test
    fun `isBookable - should return false when status is not ON_SALE`() {
        val session = createTestSession()
        setSessionStatus(session, SessionStatus.PAUSED)
        assertFalse(session.isBookable())
    }

    @Test
    fun `hasAvailableTickets - should return true when tickets available`() {
        val session = createTestSession(ticketsCount = 100)
        setTicketsSold(session, 50)
        assertTrue(session.hasAvailableTickets())
    }

    @Test
    fun `hasAvailableTickets - should return false when sold out`() {
        val session = createTestSession(ticketsCount = 100)
        setTicketsSold(session, 100)
        assertFalse(session.hasAvailableTickets())
    }

    @Test
    fun `hasAvailableTickets - should return true when ticketsCount is null (unlimited)`() {
        val session = createTestSession(ticketsCount = null)
        setTicketsSold(session, 1000)
        assertTrue(session.hasAvailableTickets())
    }

    @Test
    fun `getRemainingTickets - should return correct count`() {
        val session = createTestSession(ticketsCount = 100)
        setTicketsSold(session, 30)
        assertEquals(70, session.getRemainingTickets())
    }

    @Test
    fun `getRemainingTickets - should return null for unlimited tickets`() {
        val session = createTestSession(ticketsCount = null)
        setTicketsSold(session, 50)
        assertNull(session.getRemainingTickets())
    }

    // Helper methods
    private fun createTestSession(ticketsCount: Int? = 100): EventSession {
        val event = Event(
            title = "Test Event",
            venueId = UUID.randomUUID(),
            category = null
        )

        return EventSession(
            event = event,
            startTime = Instant.now().plusSeconds(3600), // 1 hour in future
            endTime = Instant.now().plusSeconds(7200),   // 2 hours in future
            ticketsCount = ticketsCount
        )
    }

    private fun setSessionStatus(session: EventSession, status: SessionStatus) {
        val statusField = EventSession::class.java.getDeclaredField("status")
        statusField.isAccessible = true
        statusField.set(session, status)
    }

    private fun setTicketsSold(session: EventSession, sold: Int) {
        val soldField = EventSession::class.java.getDeclaredField("ticketsSold")
        soldField.isAccessible = true
        soldField.set(session, sold)
    }
}
