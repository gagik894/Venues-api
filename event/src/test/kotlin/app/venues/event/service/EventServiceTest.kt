package app.venues.event.service

import app.venues.audit.service.AuditActionRecorder
import app.venues.common.exception.VenuesException
import app.venues.event.api.dto.EventRequest
import app.venues.event.api.mapper.EventMapper
import app.venues.event.domain.Event
import app.venues.event.domain.EventStatus
import app.venues.event.repository.EventCategoryRepository
import app.venues.event.repository.EventRepository
import app.venues.event.repository.EventSessionRepository
import app.venues.media.api.MediaApi
import app.venues.platform.api.PlatformSubscriptionApi
import app.venues.seating.api.SeatingApi
import app.venues.venue.api.VenueApi
import app.venues.venue.api.dto.VenueBasicInfoDto
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*

class EventServiceTest {

    private val eventRepository: EventRepository = mockk()
    private val eventSessionRepository: EventSessionRepository = mockk()
    private val eventCategoryRepository: EventCategoryRepository = mockk()
    private val eventSessionService: EventSessionService = mockk(relaxed = true)
    private val eventPriceService: EventPriceService = mockk()
    private val venueApi: VenueApi = mockk()
    private val seatingApi: SeatingApi = mockk(relaxed = true)
    private val mediaApi: MediaApi = mockk()
    private val eventMapper: EventMapper = mockk()
    private val eventRevalidationService: EventRevalidationService = mockk(relaxed = true)
    private val platformSubscriptionApi: PlatformSubscriptionApi = mockk(relaxed = true)
    private val auditActionRecorder: AuditActionRecorder = mockk(relaxed = true)

    private val eventService = EventService(
        eventRepository,
        eventSessionRepository,
        eventCategoryRepository,
        eventSessionService,
        eventPriceService,
        venueApi,
        seatingApi,
        mediaApi,
        eventMapper,
        eventRevalidationService,
        platformSubscriptionApi,
        auditActionRecorder
    )

    private val venueId = UUID.randomUUID()
    private val eventId = UUID.randomUUID()
    private val seatingChartId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        clearAllMocks()
    }

    @Test
    fun `createEvent should create and save event when venue exists`() {
        // Given
        val request = EventRequest(
            title = "Test Event",
            description = "Description",
            currency = "USD",
            seatingChartId = seatingChartId,
            sessions = emptyList()
        )
        
        every { venueApi.venueExists(venueId) } returns true
        every { venueApi.getVenueBasicInfo(venueId) } returns VenueBasicInfoDto(
            id = venueId,
            name = "Venue",
            slug = "venue-slug",
            address = "Address",
            latitude = 10.0,
            longitude = 20.0,
            organizationId = UUID.randomUUID(),
            merchantProfileId = null
        )
        // Capture the saved event to inspect it
        val savedEventSlot = slot<Event>()
        every { eventRepository.save(capture(savedEventSlot)) } answers { savedEventSlot.captured }

        // When
        val result = eventService.createEvent(venueId, request)

        // Then
        assertEquals("Test Event", result.title)
        assertEquals(venueId, result.venueId)
        assertEquals(EventStatus.DRAFT, result.status)
        // Verify location fallback - ensure mock is working
        assertEquals("Address", result.location) 

        verify { eventRepository.save(any()) }
        verify { eventSessionService.generateConfigsForNewSessions(any()) }
        // Fix: passing 7 arguments to match success signature (including defaulted ones potentially captured by verify)
        // action, staffId, venueId, subjectType, subjectId, organizationId, metadata
        verify { auditActionRecorder.success("EVENT_CREATED", any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `createEvent should throw ResourceNotFound when venue does not exist`() {
        // Given
        val request = EventRequest(title = "Test", currency = "USD")
        every { venueApi.venueExists(venueId) } returns false

        // When & Then
        assertThrows<VenuesException.ResourceNotFound> {
            eventService.createEvent(venueId, request)
        }
    }

    @Test
    fun `updateEvent should update fields and save when authorized`() {
        // Given
        val existingEvent = Event(title = "Old Title", venueId = venueId)
        val eventId = existingEvent.id
        val request = EventRequest(
            title = "New Title",
            description = "New Desc",
            currency = "USD"
        )

        every { eventRepository.findById(eventId) } returns Optional.of(existingEvent)
        every { venueApi.getVenueBasicInfo(venueId) } returns VenueBasicInfoDto(
            id = venueId,
            name = "V",
            slug = "slug",
            address = "Addr",
            latitude = 0.0,
            longitude = 0.0,
            organizationId = UUID.randomUUID(),
            merchantProfileId = null
        )
        every { eventRepository.save(any()) } answers { firstArg() }

        // When
        val result = eventService.updateEvent(eventId, venueId, request)

        // Then
        assertEquals("New Title", result.title)
        assertEquals("New Desc", result.description)
        verify { eventRepository.save(existingEvent) }
        verify { eventRevalidationService.onEventUpdated(existingEvent, true, any()) }
    }

    @Test
    fun `updateEvent should throw AuthorizationFailure when venueId mismatch`() {
        // Given
        val otherVenueId = UUID.randomUUID()
        val existingEvent = Event(title = "Old", venueId = otherVenueId)
        val eventId = existingEvent.id
        val request = EventRequest(title = "New", currency = "USD")

        every { eventRepository.findById(eventId) } returns Optional.of(existingEvent)

        // When & Then
        assertThrows<VenuesException.AuthorizationFailure> {
            eventService.updateEvent(eventId, venueId, request)
        }
    }

    @Test
    fun `deleteEvent should throw ResourceConflict if tickets sold`() {
        // Given
        val event = spyk(Event(title = "To Delete", venueId = venueId))
        val eventId = event.id
        // Mocking a session with sold tickets
        val sessionMock = mockk<app.venues.event.domain.EventSession>()
        every { sessionMock.ticketsSold } returns 5
        every { event.sessions } returns mutableListOf(sessionMock)

        every { eventRepository.findById(eventId) } returns Optional.of(event)

        // When & Then
        val exception = assertThrows<VenuesException.ResourceConflict> {
            eventService.deleteEvent(eventId, venueId)
        }
        assertTrue(exception.message!!.contains("tickets have been sold"))
    }

    @Test
    fun `deleteEvent should hard delete if no tickets sold`() {
        // Given
        val event = spyk(Event(title = "To Delete", venueId = venueId))
        val eventId = event.id
        val sessionMock = mockk<app.venues.event.domain.EventSession>()
        every { sessionMock.ticketsSold } returns 0
        every { event.sessions } returns mutableListOf(sessionMock)

        every { eventRepository.findById(eventId) } returns Optional.of(event)
        justRun { eventRepository.delete(event) }

        // When
        eventService.deleteEvent(eventId, venueId)

        // Then
        verify { eventRepository.delete(event) }
        // Fix: passing 7 arguments to match success signature
        verify { auditActionRecorder.success("EVENT_DELETED", any(), any(), any(), any(), any(), any()) }
    }
}
