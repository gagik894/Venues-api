package app.venues.venue.api.controller

import app.venues.audit.service.AuditActionRecorder
import app.venues.venue.api.dto.*
import app.venues.venue.api.service.VenueSecurityService
import app.venues.venue.service.VenueService
import app.venues.venue.service.VenueSettingsService
import app.venues.venue.service.VenueWebsiteService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.*

@WebMvcTest(VenueStaffController::class)
class VenueStaffControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper
) {

    @MockBean
    lateinit var venueService: VenueService
    @MockBean
    lateinit var venueSettingsService: VenueSettingsService
    @MockBean
    lateinit var venueSecurityService: VenueSecurityService
    @MockBean
    lateinit var venueWebsiteService: VenueWebsiteService

    @MockBean
    lateinit var auditActionRecorder: AuditActionRecorder

    private fun sampleAdmin(): app.venues.venue.api.dto.VenueAdminResponse =
        app.venues.venue.api.dto.VenueAdminResponse(
            id = UUID.randomUUID(),
            slug = "opera",
            name = "Opera",
            legalName = null,
            taxId = null,
            description = "desc",
            address = "addr",
            citySlug = "yerevan",
            cityName = "Yerevan",
            latitude = null,
            longitude = null,
            timeZone = "Asia/Yerevan",
            categoryCode = null,
            categoryName = null,
            phoneNumber = null,
            website = null,
            contactEmail = null,
            socialLinks = emptyMap(),
            ownershipType = null,
            notificationEmails = emptyList(),
            logoUrl = null,
            coverImageUrl = null,
            customDomain = null,
            isAlwaysOpen = false,
            status = app.venues.venue.domain.VenueStatus.ACTIVE,
            translations = listOf(
                VenueTranslationDto(language = "en", name = "Opera", description = "desc"),
                VenueTranslationDto(language = "hy", name = "Օպերա", description = "նկ.")
            ),
            createdAt = Instant.EPOCH,
            lastModifiedAt = Instant.EPOCH
        )

    private fun sampleDetail(): VenueDetailResponse =
        VenueDetailResponse(
            id = UUID.randomUUID(),
            slug = "opera",
            name = "Opera",
            description = "desc",
            logoUrl = "logo",
            coverImageUrl = "cover",
            address = "addr",
            citySlug = "yerevan",
            cityName = "Yerevan",
            latitude = null,
            longitude = null,
            timeZone = "Asia/Yerevan",
            categoryCode = null,
            categoryName = null,
            categoryColor = null,
            categoryIcon = null,
            phoneNumber = null,
            website = null,
            contactEmail = null,
            socialLinks = emptyMap(),
            isAlwaysOpen = false,
            customDomain = null,
            status = app.venues.venue.domain.VenueStatus.ACTIVE,
            translations = listOf(
                VenueTranslationDto(language = "en", name = "Opera", description = "desc"),
                VenueTranslationDto(language = "hy", name = "Օպերա", description = "նկ.")
            ),
            schedules = emptyList(),
            followerCount = 0,
            averageRating = null,
            reviewCount = 0,
            createdAt = Instant.EPOCH,
            lastModifiedAt = Instant.EPOCH
        )

    @Test
    @WithMockUser(roles = ["SUPER_ADMIN"])
    fun `create venue accepts translations and returns them`() {
        val staffId = UUID.randomUUID()
        val request = CreateVenueRequest(
            organizationId = UUID.randomUUID(),
            name = "Opera",
            slug = "opera",
            description = "desc",
            address = "addr",
            citySlug = "yerevan",
            categoryCode = null,
            legalName = null,
            taxId = null,
            latitude = null,
            longitude = null,
            phoneNumber = null,
            website = null,
            contactEmail = null,
            ownershipType = null,
            timeZone = "Asia/Yerevan",
            socialLinks = null,
            notificationEmails = null,
            logoUrl = null,
            coverImageUrl = null,
            customDomain = null,
            isAlwaysOpen = null,
            translations = listOf(
                VenueTranslationRequest(language = "en", name = "Opera", description = "desc"),
                VenueTranslationRequest(language = "hy", name = "Օպերա", description = "նկ.")
            )
        )

        val adminResponse = sampleAdmin()
        given(venueService.createVenue(any())).willReturn(adminResponse)
        given(venueService.getVenue(eq(adminResponse.id), any())).willReturn(sampleDetail())

        mockMvc.perform(
            post("/api/v1/staff/venues")
                .with(csrf())
                .requestAttr("staffId", staffId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.translations[0].language").value("en"))
            .andExpect(jsonPath("$.data.translations[1].language").value("hy"))

        val captor = argumentCaptor<CreateVenueRequest>()
        verify(venueService).createVenue(captor.capture())
        assert(captor.firstValue.translations?.size == 2)
    }

    @Test
    @WithMockUser(roles = ["SUPER_ADMIN"])
    fun `update venue accepts translations and returns them`() {
        val venueId = UUID.randomUUID()
        val staffId = UUID.randomUUID()
        val request = UpdateVenueRequest(
            translations = listOf(
                VenueTranslationRequest(language = "en", name = "Opera New", description = "desc"),
                VenueTranslationRequest(language = "ru", name = "Опера", description = null)
            )
        )

        given(venueService.updateVenue(eq(venueId), any())).willReturn(sampleAdmin().copy(id = venueId))
        given(venueService.getVenue(eq(venueId), any())).willReturn(sampleDetail())
        given(venueSecurityService.requireVenueManagementPermission(any(), eq(venueId))).willAnswer { }

        mockMvc.perform(
            put("/api/v1/staff/venues/$venueId")
                .with(csrf())
                .requestAttr("staffId", staffId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.translations").isArray)

        val captor = argumentCaptor<UpdateVenueRequest>()
        verify(venueService).updateVenue(eq(venueId), captor.capture())
        assert(captor.firstValue.translations?.size == 2)
    }
}

