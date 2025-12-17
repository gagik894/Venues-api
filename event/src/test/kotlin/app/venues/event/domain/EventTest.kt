package app.venues.event.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*

/**
 * Unit tests for Event domain entity status transitions.
 *
 * Tests the state machine behavior and validation rules.
 */
class EventTest {

    @Test
    fun `publish - should transition from DRAFT to PUBLISHED`() {
        // Given
        val event = createTestEvent()
        assertEquals(EventStatus.DRAFT, event.status)

        // When
        event.publish()

        // Then
        assertEquals(EventStatus.PUBLISHED, event.status)
    }

    @Test
    fun `publish - should throw error when not in DRAFT status`() {
        // Given
        val event = createTestEvent()
        setEventStatus(event, EventStatus.PUBLISHED)

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            event.publish()
        }
        assertTrue(exception.message!!.contains("must be DRAFT"))
    }

    @Test
    fun `suspend - should transition from PUBLISHED to SUSPENDED`() {
        // Given
        val event = createTestEvent()
        setEventStatus(event, EventStatus.PUBLISHED)

        // When
        event.suspend()

        // Then
        assertEquals(EventStatus.SUSPENDED, event.status)
    }

    @Test
    fun `suspend - should throw error when not in PUBLISHED status`() {
        // Given
        val event = createTestEvent()
        assertEquals(EventStatus.DRAFT, event.status)

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            event.suspend()
        }
        assertTrue(exception.message!!.contains("must be PUBLISHED"))
    }

    @Test
    fun `resume - should transition from SUSPENDED to PUBLISHED`() {
        // Given
        val event = createTestEvent()
        setEventStatus(event, EventStatus.SUSPENDED)

        // When
        event.resume()

        // Then
        assertEquals(EventStatus.PUBLISHED, event.status)
    }

    @Test
    fun `resume - should throw error when not in SUSPENDED status`() {
        // Given
        val event = createTestEvent()
        setEventStatus(event, EventStatus.PUBLISHED)

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            event.resume()
        }
        assertTrue(exception.message!!.contains("must be SUSPENDED"))
    }

    @Test
    fun `archive - should transition from PUBLISHED to ARCHIVED`() {
        // Given
        val event = createTestEvent()
        setEventStatus(event, EventStatus.PUBLISHED)

        // When
        event.archive()

        // Then
        assertEquals(EventStatus.ARCHIVED, event.status)
    }

    @Test
    fun `archive - should throw error when not in PUBLISHED status`() {
        // Given
        val event = createTestEvent()
        assertEquals(EventStatus.DRAFT, event.status)

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            event.archive()
        }
        assertTrue(exception.message!!.contains("must be PUBLISHED"))
    }

    @Test
    fun `markAsDeleted - should transition from any status except DELETED`() {
        // Test from DRAFT
        val event1 = createTestEvent()
        event1.markAsDeleted()
        assertEquals(EventStatus.DELETED, event1.status)

        // Test from PUBLISHED
        val event2 = createTestEvent()
        setEventStatus(event2, EventStatus.PUBLISHED)
        event2.markAsDeleted()
        assertEquals(EventStatus.DELETED, event2.status)

        // Test from SUSPENDED
        val event3 = createTestEvent()
        setEventStatus(event3, EventStatus.SUSPENDED)
        event3.markAsDeleted()
        assertEquals(EventStatus.DELETED, event3.status)

        // Test from ARCHIVED
        val event4 = createTestEvent()
        setEventStatus(event4, EventStatus.ARCHIVED)
        event4.markAsDeleted()
        assertEquals(EventStatus.DELETED, event4.status)
    }

    @Test
    fun `markAsDeleted - should throw error when already DELETED`() {
        // Given
        val event = createTestEvent()
        setEventStatus(event, EventStatus.DELETED)

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            event.markAsDeleted()
        }
        assertTrue(exception.message!!.contains("already deleted"))
    }

    @Test
    fun `isEditable - should return true for DRAFT and PUBLISHED`() {
        val event1 = createTestEvent()
        assertTrue(event1.isEditable())

        val event2 = createTestEvent()
        setEventStatus(event2, EventStatus.PUBLISHED)
        assertTrue(event2.isEditable())
    }

    @Test
    fun `isEditable - should return false for other statuses`() {
        val event1 = createTestEvent()
        setEventStatus(event1, EventStatus.SUSPENDED)
        assertFalse(event1.isEditable())

        val event2 = createTestEvent()
        setEventStatus(event2, EventStatus.ARCHIVED)
        assertFalse(event2.isEditable())

        val event3 = createTestEvent()
        setEventStatus(event3, EventStatus.DELETED)
        assertFalse(event3.isEditable())
    }

    @Test
    fun `canTransitionTo - should return correct values for DRAFT status`() {
        val event = createTestEvent()
        assertEquals(EventStatus.DRAFT, event.status)

        assertTrue(event.canTransitionTo(EventStatus.PUBLISHED))
        assertTrue(event.canTransitionTo(EventStatus.DELETED))
        assertFalse(event.canTransitionTo(EventStatus.SUSPENDED))
        assertFalse(event.canTransitionTo(EventStatus.ARCHIVED))
        assertFalse(event.canTransitionTo(EventStatus.DRAFT)) // Same status
    }

    @Test
    fun `canTransitionTo - should return correct values for PUBLISHED status`() {
        val event = createTestEvent()
        setEventStatus(event, EventStatus.PUBLISHED)

        assertTrue(event.canTransitionTo(EventStatus.SUSPENDED))
        assertTrue(event.canTransitionTo(EventStatus.ARCHIVED))
        assertTrue(event.canTransitionTo(EventStatus.DELETED))
        assertFalse(event.canTransitionTo(EventStatus.DRAFT))
        assertFalse(event.canTransitionTo(EventStatus.PUBLISHED)) // Same status
    }

    @Test
    fun `canTransitionTo - should return correct values for SUSPENDED status`() {
        val event = createTestEvent()
        setEventStatus(event, EventStatus.SUSPENDED)

        assertTrue(event.canTransitionTo(EventStatus.PUBLISHED))
        assertTrue(event.canTransitionTo(EventStatus.DELETED))
        assertFalse(event.canTransitionTo(EventStatus.DRAFT))
        assertFalse(event.canTransitionTo(EventStatus.ARCHIVED))
        assertFalse(event.canTransitionTo(EventStatus.SUSPENDED)) // Same status
    }

    @Test
    fun `canTransitionTo - should return correct values for ARCHIVED status`() {
        val event = createTestEvent()
        setEventStatus(event, EventStatus.ARCHIVED)

        assertTrue(event.canTransitionTo(EventStatus.DELETED))
        assertFalse(event.canTransitionTo(EventStatus.DRAFT))
        assertFalse(event.canTransitionTo(EventStatus.PUBLISHED))
        assertFalse(event.canTransitionTo(EventStatus.SUSPENDED))
        assertFalse(event.canTransitionTo(EventStatus.ARCHIVED)) // Same status
    }

    @Test
    fun `canTransitionTo - should return false for all transitions from DELETED`() {
        val event = createTestEvent()
        setEventStatus(event, EventStatus.DELETED)

        EventStatus.entries.forEach { targetStatus ->
            assertFalse(event.canTransitionTo(targetStatus))
        }
    }

    @Test
    fun `transitionTo - should execute valid transitions correctly`() {
        // DRAFT → PUBLISHED
        val event1 = createTestEvent()
        event1.transitionTo(EventStatus.PUBLISHED)
        assertEquals(EventStatus.PUBLISHED, event1.status)

        // PUBLISHED → SUSPENDED
        val event2 = createTestEvent()
        setEventStatus(event2, EventStatus.PUBLISHED)
        event2.transitionTo(EventStatus.SUSPENDED)
        assertEquals(EventStatus.SUSPENDED, event2.status)

        // SUSPENDED → PUBLISHED
        val event3 = createTestEvent()
        setEventStatus(event3, EventStatus.SUSPENDED)
        event3.transitionTo(EventStatus.PUBLISHED)
        assertEquals(EventStatus.PUBLISHED, event3.status)

        // PUBLISHED → ARCHIVED
        val event4 = createTestEvent()
        setEventStatus(event4, EventStatus.PUBLISHED)
        event4.transitionTo(EventStatus.ARCHIVED)
        assertEquals(EventStatus.ARCHIVED, event4.status)

        // Any → DELETED
        val event5 = createTestEvent()
        event5.transitionTo(EventStatus.DELETED)
        assertEquals(EventStatus.DELETED, event5.status)
    }

    @Test
    fun `transitionTo - should throw error for invalid transitions`() {
        // DRAFT → SUSPENDED (not allowed)
        val event1 = createTestEvent()
        assertThrows<IllegalArgumentException> {
            event1.transitionTo(EventStatus.SUSPENDED)
        }

        // SUSPENDED → ARCHIVED (not allowed)
        val event2 = createTestEvent()
        setEventStatus(event2, EventStatus.SUSPENDED)
        assertThrows<IllegalArgumentException> {
            event2.transitionTo(EventStatus.ARCHIVED)
        }

        // ARCHIVED → PUBLISHED (not allowed)
        val event3 = createTestEvent()
        setEventStatus(event3, EventStatus.ARCHIVED)
        assertThrows<IllegalArgumentException> {
            event3.transitionTo(EventStatus.PUBLISHED)
        }

        // DELETED → Any (not allowed)
        val event4 = createTestEvent()
        setEventStatus(event4, EventStatus.DELETED)
        assertThrows<IllegalArgumentException> {
            event4.transitionTo(EventStatus.PUBLISHED)
        }
    }

    @Test
    fun `transitionTo - should throw error when trying to transition to DRAFT`() {
        val event = createTestEvent()
        setEventStatus(event, EventStatus.PUBLISHED)

        val exception = assertThrows<IllegalArgumentException> {
            event.transitionTo(EventStatus.DRAFT)
        }

        assertTrue(exception.message!!.contains("Cannot transition from PUBLISHED to DRAFT"))
    }

    // Helper methods
    private fun createTestEvent(): Event {
        return Event(
            title = "Test Event",
            venueId = UUID.randomUUID(),
            category = null
        )
    }

    private fun setEventStatus(event: Event, status: EventStatus) {
        val statusField = Event::class.java.getDeclaredField("status")
        statusField.isAccessible = true
        statusField.set(event, status)
    }
}
