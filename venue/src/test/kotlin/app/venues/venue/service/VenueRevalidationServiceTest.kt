package app.venues.venue.service

import app.venues.shared.web.revalidation.RevalidationNotifier
import app.venues.location.domain.City
import app.venues.location.domain.Region
import app.venues.venue.domain.Venue
import app.venues.venue.domain.VenueTranslation
import io.mockk.Runs
import io.mockk.called
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

class VenueRevalidationServiceTest {

    private val notifier: RevalidationNotifier = mockk(relaxed = true)
    private lateinit var service: VenueRevalidationService
    private lateinit var venue: Venue

    @BeforeEach
    fun setup() {
        service = VenueRevalidationService(notifier)
        every { notifier.revalidate(any(), any(), any()) } just Runs

        val region = Region(code = "AM-ER", names = mapOf("en" to "Yerevan"))
        val city = City(region = region, slug = "yerevan", names = mapOf("en" to "Yerevan"))
        venue = Venue(
            name = "Opera",
            description = "Desc",
            slug = "opera",
            organizationId = UUID.randomUUID(),
            address = "Address",
            city = city
        ).apply {
            customDomain = "opera.am"
            translations.add(VenueTranslation(venue = this, language = "hy", name = "Օպերա", description = ""))
            activate()
        }
    }

    @Test
    fun `revalidate builds paths for active venue`() {
        val pathsSlot = slot<List<String>>()
        every { notifier.revalidate(any(), capture(pathsSlot), any()) } just Runs

        service.revalidate(venue, reason = "venue-updated")

        verify(exactly = 1) { notifier.revalidate("opera.am", any(), "venue-updated") }
        val paths = pathsSlot.captured
        assertTrue(paths.containsAll(listOf("/en", "/en/events", "/hy", "/hy/events")))
    }

    @Test
    fun `revalidate skips when domain missing`() {
        venue.customDomain = null

        service.revalidate(venue, reason = "venue-updated")

        verify { notifier wasNot called }
    }

    @Test
    fun `revalidate can force non-active venue`() {
        venue.suspend()

        service.revalidate(venue, reason = "venue-suspended", force = true)

        verify(exactly = 1) { notifier.revalidate("opera.am", any(), "venue-suspended") }
    }
}
