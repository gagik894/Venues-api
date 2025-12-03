package app.venues.event.service

import app.venues.common.exception.VenuesException
import app.venues.event.domain.Event
import app.venues.event.domain.EventSession
import app.venues.event.domain.EventStatus
import app.venues.event.domain.SessionStatus
import app.venues.event.repository.EventRepository
import app.venues.event.repository.EventSessionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.*

/**
 * Unit tests for EventStatusService.
 *
 * Tests cover:
 * - Valid status transitions
 * - Invalid status transitions
 * - Authorization checks
 * - Allowed transitions retrieval
 * - Batch operations
 */
class EventStatusServiceTest {

    private lateinit var eventRepository: EventRepository
    private lateinit var eventSessionRepository: EventSessionRepository
    private lateinit var eventStatusService: EventStatusService

    private val testVenueId = UUID.randomUUID()
    private val testEventId = UUID.randomUUID()
    private val testSessionId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        eventRepository = mockk()
        eventSessionRepository = mockk()
        eventStatusService = EventStatusService(eventRepository, eventSessionRepository)
    }

    // ===========================================
    // EVENT STATUS TESTS
    // ===========================================

    @Test
    fun `changeEventStatus - should publish draft event successfully`() {
        // Given
        val event = createTestEvent(EventStatus.DRAFT)
        every { eventRepository.findById(testEventId) } returns Optional.of(event)
        every { eventRepository.save(any()) } returns event

        // When
        val result = eventStatusService.changeEventStatus(
            eventId = testEventId,
            venueId = testVenueId,
            targetStatus = EventStatus.PUBLISHED,
            reason = "Ready for public"
        )

        // Then
        assertEquals(EventStatus.PUBLISHED, result.status)
        verify(exactly = 1) { eventRepository.save(event) }
    }

    @Test
    fun `changeEventStatus - should suspend published event successfully`() {
        // Given
        val event = createTestEvent(EventStatus.PUBLISHED)
        every { eventRepository.findById(testEventId) } returns Optional.of(event)
        every { eventRepository.save(any()) } returns event

        // When
        val result = eventStatusService.changeEventStatus(
            eventId = testEventId,
            venueId = testVenueId,
            targetStatus = EventStatus.SUSPENDED,
            reason = "Maintenance required"
        )

        // Then
        assertEquals(EventStatus.SUSPENDED, result.status)
        verify(exactly = 1) { eventRepository.save(event) }
    }

    @Test
    fun `changeEventStatus - should resume suspended event successfully`() {
        // Given
        val event = createTestEvent(EventStatus.SUSPENDED)
        every { eventRepository.findById(testEventId) } returns Optional.of(event)
        every { eventRepository.save(any()) } returns event

        // When
        val result = eventStatusService.changeEventStatus(
            eventId = testEventId,
            venueId = testVenueId,
            targetStatus = EventStatus.PUBLISHED,
            reason = "Maintenance completed"
        )

        // Then
        assertEquals(EventStatus.PUBLISHED, result.status)
        verify(exactly = 1) { eventRepository.save(event) }
    }

    @Test
    fun `changeEventStatus - should archive published event successfully`() {
        // Given
        val event = createTestEvent(EventStatus.PUBLISHED)
        every { eventRepository.findById(testEventId) } returns Optional.of(event)
        every { eventRepository.save(any()) } returns event

        // When
        val result = eventStatusService.changeEventStatus(
            eventId = testEventId,
            venueId = testVenueId,
            targetStatus = EventStatus.ARCHIVED,
            reason = "Event finished"
        )

        // Then
        assertEquals(EventStatus.ARCHIVED, result.status)
        verify(exactly = 1) { eventRepository.save(event) }
    }

    @Test
    fun `changeEventStatus - should throw error for invalid transition`() {
        // Given
        val event = createTestEvent(EventStatus.DRAFT)
        every { eventRepository.findById(testEventId) } returns Optional.of(event)

        // When & Then
        val exception = assertThrows<VenuesException.ValidationFailure> {
            eventStatusService.changeEventStatus(
                eventId = testEventId,
                venueId = testVenueId,
                targetStatus = EventStatus.ARCHIVED,
                reason = null
            )
        }

        assertTrue(exception.message!!.contains("Cannot transition"))
    }

    @Test
    fun `changeEventStatus - should throw error when event not found`() {
        // Given
        every { eventRepository.findById(testEventId) } returns Optional.empty()

        // When & Then
        assertThrows<VenuesException.ResourceNotFound> {
            eventStatusService.changeEventStatus(
                eventId = testEventId,
                venueId = testVenueId,
                targetStatus = EventStatus.PUBLISHED,
                reason = null
            )
        }
    }

    @Test
    fun `changeEventStatus - should throw error when venue does not match`() {
        // Given
        val event = createTestEvent(EventStatus.DRAFT)
        every { eventRepository.findById(testEventId) } returns Optional.of(event)

        val wrongVenueId = UUID.randomUUID()

        // When & Then
        assertThrows<VenuesException.AuthorizationFailure> {
            eventStatusService.changeEventStatus(
                eventId = testEventId,
                venueId = wrongVenueId,
                targetStatus = EventStatus.PUBLISHED,
                reason = null
            )
        }
    }

    @Test
    fun `getAllowedEventTransitions - should return correct transitions for DRAFT status`() {
        // Given
        val event = createTestEvent(EventStatus.DRAFT)
        every { eventRepository.findById(testEventId) } returns Optional.of(event)

        // When
        val transitions = eventStatusService.getAllowedEventTransitions(testEventId, testVenueId)

        // Then
        assertEquals(2, transitions.size)
        assertTrue(transitions.contains(EventStatus.PUBLISHED))
        assertTrue(transitions.contains(EventStatus.DELETED))
    }

    @Test
    fun `getAllowedEventTransitions - should return correct transitions for PUBLISHED status`() {
        // Given
        val event = createTestEvent(EventStatus.PUBLISHED)
        every { eventRepository.findById(testEventId) } returns Optional.of(event)

        // When
        val transitions = eventStatusService.getAllowedEventTransitions(testEventId, testVenueId)

        // Then
        assertEquals(3, transitions.size)
        assertTrue(transitions.contains(EventStatus.SUSPENDED))
        assertTrue(transitions.contains(EventStatus.ARCHIVED))
        assertTrue(transitions.contains(EventStatus.DELETED))
    }

    @Test
    fun `getAllowedEventTransitions - should return correct transitions for SUSPENDED status`() {
        // Given
        val event = createTestEvent(EventStatus.SUSPENDED)
        every { eventRepository.findById(testEventId) } returns Optional.of(event)

        // When
        val transitions = eventStatusService.getAllowedEventTransitions(testEventId, testVenueId)

        // Then
        assertEquals(2, transitions.size)
        assertTrue(transitions.contains(EventStatus.PUBLISHED))
        assertTrue(transitions.contains(EventStatus.DELETED))
    }

    @Test
    fun `getAllowedEventTransitions - should return correct transitions for ARCHIVED status`() {
        // Given
        val event = createTestEvent(EventStatus.ARCHIVED)
        every { eventRepository.findById(testEventId) } returns Optional.of(event)

        // When
        val transitions = eventStatusService.getAllowedEventTransitions(testEventId, testVenueId)

        // Then
        assertEquals(1, transitions.size)
        assertTrue(transitions.contains(EventStatus.DELETED))
    }

    @Test
    fun `getAllowedEventTransitions - should return empty for DELETED status`() {
        // Given
        val event = createTestEvent(EventStatus.DELETED)
        every { eventRepository.findById(testEventId) } returns Optional.of(event)

        // When
        val transitions = eventStatusService.getAllowedEventTransitions(testEventId, testVenueId)

        // Then
        assertTrue(transitions.isEmpty())
    }

    // ===========================================
    // SESSION STATUS TESTS
    // ===========================================

    @Test
    fun `changeSessionStatus - should pause ON_SALE session successfully`() {
        // Given
        val session = createTestSession(SessionStatus.ON_SALE)
        every { eventSessionRepository.findById(testSessionId) } returns Optional.of(session)
        every { eventSessionRepository.save(any()) } returns session

        // When
        val result = eventStatusService.changeSessionStatus(
            sessionId = testSessionId,
            venueId = testVenueId,
            targetStatus = SessionStatus.PAUSED,
            reason = "Technical issue"
        )

        // Then
        assertEquals(SessionStatus.PAUSED, result.status)
        verify(exactly = 1) { eventSessionRepository.save(session) }
    }

    @Test
    fun `changeSessionStatus - should resume PAUSED session successfully`() {
        // Given
        val session = createTestSession(SessionStatus.PAUSED)
        every { eventSessionRepository.findById(testSessionId) } returns Optional.of(session)
        every { eventSessionRepository.save(any()) } returns session

        // When
        val result = eventStatusService.changeSessionStatus(
            sessionId = testSessionId,
            venueId = testVenueId,
            targetStatus = SessionStatus.ON_SALE,
            reason = "Issue resolved"
        )

        // Then
        assertEquals(SessionStatus.ON_SALE, result.status)
        verify(exactly = 1) { eventSessionRepository.save(session) }
    }

    @Test
    fun `changeSessionStatus - should mark session as SOLD_OUT successfully`() {
        // Given
        val session = createTestSession(SessionStatus.ON_SALE)
        every { eventSessionRepository.findById(testSessionId) } returns Optional.of(session)
        every { eventSessionRepository.save(any()) } returns session

        // When
        val result = eventStatusService.changeSessionStatus(
            sessionId = testSessionId,
            venueId = testVenueId,
            targetStatus = SessionStatus.SOLD_OUT,
            reason = "All tickets sold"
        )

        // Then
        assertEquals(SessionStatus.SOLD_OUT, result.status)
        verify(exactly = 1) { eventSessionRepository.save(session) }
    }

    @Test
    fun `changeSessionStatus - should close sales successfully`() {
        // Given
        val session = createTestSession(SessionStatus.ON_SALE)
        every { eventSessionRepository.findById(testSessionId) } returns Optional.of(session)
        every { eventSessionRepository.save(any()) } returns session

        // When
        val result = eventStatusService.changeSessionStatus(
            sessionId = testSessionId,
            venueId = testVenueId,
            targetStatus = SessionStatus.SALES_CLOSED,
            reason = "Event started"
        )

        // Then
        assertEquals(SessionStatus.SALES_CLOSED, result.status)
        verify(exactly = 1) { eventSessionRepository.save(session) }
    }

    @Test
    fun `changeSessionStatus - should cancel session successfully`() {
        // Given
        val session = createTestSession(SessionStatus.ON_SALE)
        every { eventSessionRepository.findById(testSessionId) } returns Optional.of(session)
        every { eventSessionRepository.save(any()) } returns session

        // When
        val result = eventStatusService.changeSessionStatus(
            sessionId = testSessionId,
            venueId = testVenueId,
            targetStatus = SessionStatus.CANCELLED,
            reason = "Event cancelled by organizer"
        )

        // Then
        assertEquals(SessionStatus.CANCELLED, result.status)
        verify(exactly = 1) { eventSessionRepository.save(session) }
    }

    @Test
    fun `changeSessionStatus - should throw error for invalid transition`() {
        // Given
        val session = createTestSession(SessionStatus.SOLD_OUT)
        every { eventSessionRepository.findById(testSessionId) } returns Optional.of(session)

        // When & Then
        val exception = assertThrows<VenuesException.ValidationFailure> {
            eventStatusService.changeSessionStatus(
                sessionId = testSessionId,
                venueId = testVenueId,
                targetStatus = SessionStatus.ON_SALE,
                reason = null
            )
        }

        assertTrue(exception.message!!.contains("Cannot transition"))
    }

    @Test
    fun `changeSessionStatus - should throw error when session not found`() {
        // Given
        every { eventSessionRepository.findById(testSessionId) } returns Optional.empty()

        // When & Then
        assertThrows<VenuesException.ResourceNotFound> {
            eventStatusService.changeSessionStatus(
                sessionId = testSessionId,
                venueId = testVenueId,
                targetStatus = SessionStatus.PAUSED,
                reason = null
            )
        }
    }

    @Test
    fun `changeSessionStatus - should throw error when venue does not match`() {
        // Given
        val session = createTestSession(SessionStatus.ON_SALE)
        every { eventSessionRepository.findById(testSessionId) } returns Optional.of(session)

        val wrongVenueId = UUID.randomUUID()

        // When & Then
        assertThrows<VenuesException.AuthorizationFailure> {
            eventStatusService.changeSessionStatus(
                sessionId = testSessionId,
                venueId = wrongVenueId,
                targetStatus = SessionStatus.PAUSED,
                reason = null
            )
        }
    }

    @Test
    fun `getAllowedSessionTransitions - should return correct transitions for ON_SALE status`() {
        // Given
        val session = createTestSession(SessionStatus.ON_SALE)
        every { eventSessionRepository.findById(testSessionId) } returns Optional.of(session)

        // When
        val transitions = eventStatusService.getAllowedSessionTransitions(testSessionId, testVenueId)

        // Then
        assertEquals(4, transitions.size)
        assertTrue(transitions.contains(SessionStatus.PAUSED))
        assertTrue(transitions.contains(SessionStatus.SOLD_OUT))
        assertTrue(transitions.contains(SessionStatus.SALES_CLOSED))
        assertTrue(transitions.contains(SessionStatus.CANCELLED))
    }

    @Test
    fun `getAllowedSessionTransitions - should return correct transitions for PAUSED status`() {
        // Given
        val session = createTestSession(SessionStatus.PAUSED)
        every { eventSessionRepository.findById(testSessionId) } returns Optional.of(session)

        // When
        val transitions = eventStatusService.getAllowedSessionTransitions(testSessionId, testVenueId)

        // Then
        assertEquals(4, transitions.size)
        assertTrue(transitions.contains(SessionStatus.ON_SALE))
        assertTrue(transitions.contains(SessionStatus.SOLD_OUT))
        assertTrue(transitions.contains(SessionStatus.SALES_CLOSED))
        assertTrue(transitions.contains(SessionStatus.CANCELLED))
    }

    @Test
    fun `getAllowedSessionTransitions - should return correct transitions for SOLD_OUT status`() {
        // Given
        val session = createTestSession(SessionStatus.SOLD_OUT)
        every { eventSessionRepository.findById(testSessionId) } returns Optional.of(session)

        // When
        val transitions = eventStatusService.getAllowedSessionTransitions(testSessionId, testVenueId)

        // Then
        assertEquals(2, transitions.size)
        assertTrue(transitions.contains(SessionStatus.SALES_CLOSED))
        assertTrue(transitions.contains(SessionStatus.CANCELLED))
    }

    @Test
    fun `getAllowedSessionTransitions - should return correct transitions for SALES_CLOSED status`() {
        // Given
        val session = createTestSession(SessionStatus.SALES_CLOSED)
        every { eventSessionRepository.findById(testSessionId) } returns Optional.of(session)

        // When
        val transitions = eventStatusService.getAllowedSessionTransitions(testSessionId, testVenueId)

        // Then
        assertEquals(1, transitions.size)
        assertTrue(transitions.contains(SessionStatus.CANCELLED))
    }

    @Test
    fun `getAllowedSessionTransitions - should return empty for CANCELLED status`() {
        // Given
        val session = createTestSession(SessionStatus.CANCELLED)
        every { eventSessionRepository.findById(testSessionId) } returns Optional.of(session)

        // When
        val transitions = eventStatusService.getAllowedSessionTransitions(testSessionId, testVenueId)

        // Then
        assertTrue(transitions.isEmpty())
    }

    // ===========================================
    // BATCH OPERATIONS TESTS
    // ===========================================

    @Test
    fun `bulkChangeSessionStatus - should update all sessions successfully`() {
        // Given
        val sessionIds = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        val sessions = sessionIds.map { createTestSession(SessionStatus.ON_SALE, it) }

        sessionIds.forEachIndexed { index, id ->
            every { eventSessionRepository.findById(id) } returns Optional.of(sessions[index])
            every { eventSessionRepository.save(sessions[index]) } returns sessions[index]
        }

        // When
        val results = eventStatusService.bulkChangeSessionStatus(
            sessionIds = sessionIds,
            venueId = testVenueId,
            targetStatus = SessionStatus.PAUSED,
            reason = "Bulk pause for maintenance"
        )

        // Then
        assertEquals(3, results.size)
        results.values.forEach { result ->
            assertTrue(result.isSuccess)
            assertEquals(SessionStatus.PAUSED, result.getOrNull()?.status)
        }
        verify(exactly = 3) { eventSessionRepository.save(any()) }
    }

    @Test
    fun `bulkChangeSessionStatus - should handle partial failures`() {
        // Given - test transition to PAUSED status
        // PAUSED can be reached from ON_SALE but not from CANCELLED or already PAUSED
        val sessionIds = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        val session1 = createTestSession(SessionStatus.ON_SALE, sessionIds[0])      // ON_SALE → PAUSED: valid
        val session2 = createTestSession(SessionStatus.CANCELLED, sessionIds[1])    // CANCELLED → PAUSED: invalid
        val session3 = createTestSession(SessionStatus.SOLD_OUT, sessionIds[2])     // SOLD_OUT → PAUSED: invalid

        every { eventSessionRepository.findById(sessionIds[0]) } returns Optional.of(session1)
        every { eventSessionRepository.findById(sessionIds[1]) } returns Optional.of(session2)
        every { eventSessionRepository.findById(sessionIds[2]) } returns Optional.of(session3)
        every { eventSessionRepository.save(any()) } returnsArgument 0

        // When
        val results = eventStatusService.bulkChangeSessionStatus(
            sessionIds = sessionIds,
            venueId = testVenueId,
            targetStatus = SessionStatus.PAUSED,
            reason = "Pause sales"
        )

        // Then
        assertEquals(3, results.size)
        assertTrue(results[sessionIds[0]]!!.isSuccess)  // ON_SALE → PAUSED succeeds
        assertTrue(results[sessionIds[1]]!!.isFailure)  // CANCELLED → PAUSED fails
        assertTrue(results[sessionIds[2]]!!.isFailure)  // SOLD_OUT → PAUSED fails
    }

    // ===========================================
    // HELPER METHODS
    // ===========================================

    private fun createTestEvent(status: EventStatus): Event {
        val event = Event(
            title = "Test Event",
            venueId = testVenueId,
            category = null
        )
        // Use reflection to set protected status field
        val statusField = Event::class.java.getDeclaredField("status")
        statusField.isAccessible = true
        statusField.set(event, status)

        val idField = Event::class.java.superclass.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(event, testEventId)

        return event
    }

    private fun createTestSession(
        status: SessionStatus,
        sessionId: UUID = testSessionId
    ): EventSession {
        val event = createTestEvent(EventStatus.PUBLISHED)
        val session = EventSession(
            event = event,
            startTime = Instant.now().plusSeconds(3600),
            endTime = Instant.now().plusSeconds(7200)
        )

        // Use reflection to set protected status field
        val statusField = EventSession::class.java.getDeclaredField("status")
        statusField.isAccessible = true
        statusField.set(session, status)

        val idField = EventSession::class.java.superclass.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(session, sessionId)

        return session
    }
}
