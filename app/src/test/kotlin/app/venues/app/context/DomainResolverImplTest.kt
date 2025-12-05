package app.venues.app.context

import app.venues.venue.api.VenueApi
import app.venues.venue.api.dto.VenueBasicInfoDto
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.*

class DomainResolverImplTest {

    private val venueApi: VenueApi = io.mockk.mockk(relaxed = true)
    private val resolver = DomainResolverImpl(venueApi)

    @Test
    fun `resolves domain after canonicalization`() {
        val venueId = UUID.randomUUID()
        every { venueApi.getVenueByDomain("opera.am") } returns VenueBasicInfoDto(
            id = venueId,
            name = "Opera",
            slug = "opera",
            address = null,
            latitude = null,
            longitude = null,
            organizationId = UUID.randomUUID(),
            merchantProfileId = null
        )

        val resolved = resolver.resolve("HTTPS://Opera.AM:443/home")

        assertEquals(venueId, resolved?.venueId)
        verify(exactly = 1) { venueApi.getVenueByDomain("opera.am") }
    }

    @Test
    fun `skip resolution for malformed domains`() {
        val resolved = resolver.resolve("bad domain.com")

        assertNull(resolved)
        verify(exactly = 0) { venueApi.getVenueByDomain(any()) }
    }

    @Test
    fun `invalidation uses canonical domain`() {
        val venueId1 = UUID.randomUUID()
        val venueId2 = UUID.randomUUID()
        every { venueApi.getVenueByDomain("opera.am") } returnsMany listOf(
            VenueBasicInfoDto(
                id = venueId1,
                name = "Opera",
                slug = "opera",
                address = null,
                latitude = null,
                longitude = null,
                organizationId = UUID.randomUUID(),
                merchantProfileId = null
            ),
            VenueBasicInfoDto(
                id = venueId2,
                name = "Opera",
                slug = "opera",
                address = null,
                latitude = null,
                longitude = null,
                organizationId = UUID.randomUUID(),
                merchantProfileId = null
            )
        )

        val first = resolver.resolve("opera.am")
        resolver.invalidate("OPERA.AM:443")
        val second = resolver.resolve("opera.am")

        assertEquals(venueId1, first?.venueId)
        assertEquals(venueId2, second?.venueId)
        verify(exactly = 2) { venueApi.getVenueByDomain("opera.am") }
    }
}
