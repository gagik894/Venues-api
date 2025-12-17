package app.venues.event.service

import app.venues.event.domain.Event
import app.venues.event.domain.EventStatus
import app.venues.event.domain.EventTranslation
import app.venues.venue.api.VenueApi
import app.venues.venue.api.dto.VenueBasicInfoDto
import app.venues.shared.web.revalidation.RevalidationNotifier
import io.mockk.Runs
import io.mockk.called
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.util.*

class EventRevalidationServiceTest {

    private val revalidationNotifier: RevalidationNotifier = mockk(relaxed = true)
    private val venueApi: VenueApi = mockk()
    private lateinit var service: EventRevalidationService

    private val venueId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        service = EventRevalidationService(revalidationNotifier, venueApi)
        every { venueApi.getVenueBasicInfo(venueId) } returns VenueBasicInfoDto(
            id = venueId,
            name = "Opera",
            slug = "opera",
            address = null,
            latitude = null,
            longitude = null,
            organizationId = UUID.randomUUID(),
            merchantProfileId = null,
            customDomain = "opera.am"
        )
        every { revalidationNotifier.revalidate(any(), any(), any()) } just Runs
    }

    @Test
    fun `onEventUpdated triggers revalidation for published event with translations`() {
        val event = createEvent(status = EventStatus.PUBLISHED)
        event.addTranslation(EventTranslation(event, "hy", "T", "D"))
        val eventId = event.id

        val pathsSlot = slot<List<String>>()

        every { revalidationNotifier.revalidate(any(), capture(pathsSlot), any()) } just Runs

        service.onEventUpdated(event, includeDetail = true, reason = "event-updated")

        verify(exactly = 1) { revalidationNotifier.revalidate("opera.am", any(), "event-updated") }
        val paths = pathsSlot.captured
        assertTrue(paths.containsAll(listOf("/en", "/en/events", "/en/events/$eventId")))
        assertTrue(paths.containsAll(listOf("/hy", "/hy/events", "/hy/events/$eventId")))
    }

    @Test
    fun `onEventUpdated skips draft events`() {
        val event = createEvent(status = EventStatus.DRAFT)

        service.onEventUpdated(event, includeDetail = true, reason = "event-updated")

        verify { revalidationNotifier wasNot called }
    }

    @Test
    fun `onPublishFromDraft revalidates listings only`() {
        val event = createEvent(status = EventStatus.PUBLISHED)
        val eventId = event.id
        val pathsSlot = slot<List<String>>()
        every { revalidationNotifier.revalidate(any(), capture(pathsSlot), any()) } just Runs

        service.onPublishFromDraft(event)

        val paths = pathsSlot.captured
        assertTrue(paths.containsAll(listOf("/en", "/en/events")))
        assertFalse(paths.any { it.contains(eventId.toString()) })
    }

    private fun createEvent(status: EventStatus): Event {
        val event = Event(
            title = "Test",
            description = "Desc",
            imgUrl = null,
            venueId = venueId,
            location = "Addr",
            latitude = null,
            longitude = null,
            priceRange = null,
            currency = "AMD",
            seatingChartId = null,
            category = null
        )
        when (status) {
            EventStatus.DRAFT -> {}
            EventStatus.PUBLISHED -> event.publish()
            EventStatus.SUSPENDED -> {
                event.publish()
                event.suspend()
            }
            EventStatus.ARCHIVED -> {
                event.publish()
                event.archive()
            }
            EventStatus.DELETED -> event.markAsDeleted()
        }
        return event
    }
}
