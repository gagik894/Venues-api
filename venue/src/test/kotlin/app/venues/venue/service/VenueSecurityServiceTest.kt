package app.venues.venue.service

import app.venues.common.exception.VenuesException
import app.venues.staff.api.StaffSecurityFacade
import app.venues.venue.api.VenueApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*

class VenueSecurityServiceTest {

    private val venueApi: VenueApi = mockk()
    private val staffSecurityFacade: StaffSecurityFacade = mockk()

    private lateinit var service: VenueSecurityServiceImpl

    private val staffId = UUID.randomUUID()
    private val venueId = UUID.randomUUID()
    private val organizationId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        service = VenueSecurityServiceImpl(venueApi, staffSecurityFacade)
    }

    @Nested
    inner class RequireVenueManagementPermission {

        @Test
        fun `succeeds when staff has permission`() {
            every { venueApi.getVenueOrganizationId(venueId) } returns organizationId
            every { staffSecurityFacade.canManageVenue(staffId, venueId, organizationId) } returns true

            // Should not throw
            assertDoesNotThrow {
                service.requireVenueManagementPermission(staffId, venueId)
            }

            verify { staffSecurityFacade.canManageVenue(staffId, venueId, organizationId) }
        }

        @Test
        fun `throws AuthorizationFailure when staff lacks permission`() {
            every { venueApi.getVenueOrganizationId(venueId) } returns organizationId
            every { staffSecurityFacade.canManageVenue(staffId, venueId, organizationId) } returns false

            val exception = assertThrows<VenuesException.AuthorizationFailure> {
                service.requireVenueManagementPermission(staffId, venueId)
            }

            assertTrue(exception.message!!.contains("don't have permission"))
        }

        @Test
        fun `throws ResourceNotFound when venue has no organization`() {
            every { venueApi.getVenueOrganizationId(venueId) } returns null

            val exception = assertThrows<VenuesException.ResourceNotFound> {
                service.requireVenueManagementPermission(staffId, venueId)
            }

            assertTrue(exception.message!!.contains("Venue not found"))
        }

        @Test
        fun `checks correct organization for permission`() {
            val differentOrgId = UUID.randomUUID()
            every { venueApi.getVenueOrganizationId(venueId) } returns differentOrgId
            every { staffSecurityFacade.canManageVenue(staffId, venueId, differentOrgId) } returns true

            service.requireVenueManagementPermission(staffId, venueId)

            verify { staffSecurityFacade.canManageVenue(staffId, venueId, differentOrgId) }
        }

        @Test
        fun `super admin can manage any venue`() {
            // Simulate super admin check returning true
            every { venueApi.getVenueOrganizationId(venueId) } returns organizationId
            every { staffSecurityFacade.canManageVenue(staffId, venueId, organizationId) } returns true

            assertDoesNotThrow {
                service.requireVenueManagementPermission(staffId, venueId)
            }
        }
    }
}
