package app.venues.staff.service

import app.venues.staff.api.dto.VenuePermissionDto
import app.venues.staff.domain.OrganizationRole
import app.venues.staff.domain.StaffMembership
import app.venues.staff.domain.StaffVenuePermission
import app.venues.staff.domain.VenueRole
import app.venues.staff.repository.StaffIdentityRepository
import app.venues.staff.repository.StaffVenuePermissionRepository
import app.venues.venue.api.VenueApi
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.util.*

class StaffManagementServiceTest {

    private lateinit var staffRepository: StaffIdentityRepository
    private lateinit var staffContextBuilder: StaffContextBuilder
    private lateinit var venuePermissionRepository: StaffVenuePermissionRepository
    private lateinit var venueApi: VenueApi
    private lateinit var service: StaffManagementService

    private val actorId = UUID.randomUUID()
    private val venueId = UUID.randomUUID()
    private val orgId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        staffRepository = mockk()
        staffContextBuilder = mockk(relaxed = true)
        venuePermissionRepository = mockk()
        venueApi = mockk()
        service = StaffManagementService(
            staffRepository = staffRepository,
            staffContextBuilder = staffContextBuilder,
            venuePermissionRepository = venuePermissionRepository,
            venueApi = venueApi
        )
    }

    @Test
    fun `listVenuePermissions returns page metadata aligned to permissions`() {
        // Actor is super admin to bypass org check details
        val actor = app.venues.staff.domain.StaffIdentity(
            email = "actor@test.com",
            passwordHash = "hash"
        ).apply { isPlatformSuperAdmin = true }

        // Target staff with one permission
        val targetStaff = app.venues.staff.domain.StaffIdentity(
            email = "user@test.com",
            passwordHash = "hash"
        )
        val membership = StaffMembership(
            staff = targetStaff,
            organizationId = orgId,
            orgRole = OrganizationRole.MEMBER,
            isActive = true
        )
        val permission = StaffVenuePermission(
            membership = membership,
            venueId = venueId,
            role = VenueRole.MANAGER
        )
        membership.venuePermissions.add(permission)
        targetStaff.memberships.add(membership)

        val pageable = PageRequest.of(0, 2)
        val permissionPage = PageImpl(listOf(permission), pageable, 3) // pretend 3 total permissions

        every { venueApi.getVenueOrganizationId(venueId) } returns orgId
        every { staffRepository.findById(actorId) } returns Optional.of(actor)
        every { venuePermissionRepository.findByVenueId(venueId, pageable) } returns permissionPage

        val result = service.listVenuePermissions(actorId, venueId, pageable)

        assertEquals(1, result.content.size)
        assertEquals(3, result.totalElements)
        val dto: VenuePermissionDto = result.content.first()
        assertEquals(targetStaff.id, dto.staffId)
        assertEquals(targetStaff.email, dto.staffEmail)
        assertEquals(VenueRole.MANAGER, dto.role)
    }
}

