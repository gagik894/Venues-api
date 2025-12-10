package app.venues.event.service

import app.venues.event.api.dto.EventRequest
import app.venues.event.api.mapper.EventMapper
import app.venues.event.domain.Event
import app.venues.event.repository.EventCategoryRepository
import app.venues.event.repository.EventRepository
import app.venues.event.repository.EventSessionRepository
import app.venues.media.api.MediaApi
import app.venues.platform.api.PlatformSubscriptionApi
import app.venues.seating.api.SeatingApi
import app.venues.venue.api.VenueApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

class EventServiceSubscriptionTest {

    private lateinit var eventRepository: EventRepository
    private lateinit var eventSessionRepository: EventSessionRepository
    private lateinit var eventCategoryRepository: EventCategoryRepository
    private lateinit var eventSessionService: EventSessionService
    private lateinit var eventPriceService: EventPriceService
    private lateinit var venueApi: VenueApi
    private lateinit var seatingApi: SeatingApi
    private lateinit var mediaApi: MediaApi
    private lateinit var eventMapper: EventMapper
    private lateinit var eventRevalidationService: EventRevalidationService
    private lateinit var platformSubscriptionApi: PlatformSubscriptionApi
    private lateinit var service: EventService

    private val venueId = UUID.randomUUID()
    private val eventId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        eventRepository = mockk(relaxed = true)
        eventSessionRepository = mockk(relaxed = true)
        eventCategoryRepository = mockk(relaxed = true)
        eventSessionService = mockk(relaxed = true)
        eventPriceService = mockk(relaxed = true)
        venueApi = mockk(relaxed = true)
        seatingApi = mockk(relaxed = true)
        mediaApi = mockk(relaxed = true)
        eventMapper = mockk(relaxed = true)
        eventRevalidationService = mockk(relaxed = true)
        platformSubscriptionApi = mockk(relaxed = true)

        service = EventService(
            eventRepository = eventRepository,
            eventSessionRepository = eventSessionRepository,
            eventCategoryRepository = eventCategoryRepository,
            eventSessionService = eventSessionService,
            eventPriceService = eventPriceService,
            venueApi = venueApi,
            seatingApi = seatingApi,
            mediaApi = mediaApi,
            eventMapper = eventMapper,
            eventRevalidationService = eventRevalidationService,
            platformSubscriptionApi = platformSubscriptionApi
        )
    }

    @Test
    fun `createEvent calls platformSubscriptionApi with request platforms`() {
        val platformIds = listOf(UUID.randomUUID(), UUID.randomUUID())
        val request = EventRequest(
            title = "Title",
            sessions = emptyList(),
            translations = emptyList(),
            subscribedPlatformIds = platformIds
        )

        every { venueApi.venueExists(venueId) } returns true
        every { eventRepository.save(any()) } answers { firstArg<Event>() }

        val event = service.createEvent(venueId, request)

        verify(exactly = 1) { platformSubscriptionApi.updateEventSubscriptions(event.id, platformIds) }
        assert(event.id != null)
    }

    @Test
    fun `updateEvent calls platformSubscriptionApi with request platforms`() {
        val platformIds = listOf(UUID.randomUUID())
        val request = EventRequest(
            title = "Title",
            sessions = emptyList(),
            translations = emptyList(),
            subscribedPlatformIds = platformIds
        )

        val existing = Event(
            title = "Old",
            venueId = venueId,
            category = null,
            imgUrl = null,
            secondaryImgUrls = mutableSetOf(),
            description = null,
            location = null,
            latitude = null,
            longitude = null,
            tags = mutableSetOf(),
            priceRange = null,
            currency = "USD",
            seatingChartId = null,
            merchantProfileId = null,
            firstSessionStart = null,
            lastSessionEnd = null
        )

        every { eventRepository.findById(eventId) } returns java.util.Optional.of(existing)
        every { eventRepository.save(any()) } answers { firstArg<Event>() }

        val updated = service.updateEvent(eventId, venueId, request)

        verify(exactly = 1) { platformSubscriptionApi.updateEventSubscriptions(updated.id, platformIds) }
    }

    @Test
    fun `getPlatformSubscriptions delegates to platformSubscriptionApi`() {
        val platforms = listOf(UUID.randomUUID())
        every { platformSubscriptionApi.getEventSubscriptions(eventId) } returns platforms

        val result = service.getPlatformSubscriptions(eventId)

        assert(result == platforms)
    }
}

