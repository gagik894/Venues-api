package app.venues.event.api.controller

import app.venues.audit.port.api.StaffAuditPort
import app.venues.event.api.EventApi
import app.venues.event.api.dto.*
import app.venues.event.api.mapper.EventMapper
import app.venues.event.domain.*
import app.venues.event.service.EventPricingService
import app.venues.event.service.EventService
import app.venues.event.service.EventStatusService
import app.venues.platform.api.PlatformSubscriptionApi
import app.venues.seating.api.SeatingApi
import app.venues.shared.money.MoneyAmount
import app.venues.venue.api.VenueApi
import app.venues.venue.api.service.VenueSecurityService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.reset
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Instant
import java.util.*

@SpringBootApplication
class VenueEventControllerAuditTestConfig

@ExtendWith(SpringExtension::class)
@WebMvcTest(VenueEventController::class)
@EnableAspectJAutoProxy(proxyTargetClass = true)
@Import(app.venues.audit.aspect.AuditableAspect::class)
class VenueEventControllerAuditTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper
) {

    @MockBean
    lateinit var eventService: EventService

    @MockBean
    lateinit var eventPricingService: EventPricingService

    @MockBean
    lateinit var eventStatusService: EventStatusService

    @MockBean
    lateinit var venueSecurityService: VenueSecurityService

    @MockBean
    lateinit var eventMapper: EventMapper

    @MockBean
    lateinit var venueApi: VenueApi

    @MockBean
    lateinit var seatingApi: SeatingApi

    @MockBean
    lateinit var platformSubscriptionApi: PlatformSubscriptionApi

    @MockBean
    lateinit var eventApi: EventApi

    @MockBean
    lateinit var staffAuditPort: StaffAuditPort

    private val venueId = UUID.randomUUID()
    private val eventId = UUID.randomUUID()
    private val sessionId = UUID.randomUUID()
    private val staffId = UUID.randomUUID()
    private val templateId = UUID.randomUUID()

    @BeforeEach
    fun resetMocks() {
        reset(
            staffAuditPort,
            eventMapper,
            eventService,
            eventPricingService,
            eventStatusService,
            venueApi,
            seatingApi,
            platformSubscriptionApi,
            eventApi
        )
    }

    @Test
    @WithMockUser(roles = ["STAFF"])
    fun `create event records audit`() {
        val request = EventRequest(title = "Show", currency = "AMD")
        val event = event()
        whenever(eventService.createEvent(eq(venueId), any(), anyOrNull(), anyOrNull())).thenReturn(event)
        whenever(eventMapper.toResponse(eq(event), any(), anyOrNull(), any(), anyOrNull(), any())).thenReturn(
            eventResponse(event.id)
        )
        whenever(venueApi.getVenueName(eq(venueId))).thenReturn("Venue")
        whenever(platformSubscriptionApi.getEventSubscriptions(eq(event.id))).thenReturn(emptyList())

        val dataPart =
            MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(request))

        mockMvc.perform(
            multipart("/api/v1/staff/venues/$venueId/events")
                .file(dataPart)
                .with(csrf())
                .requestAttr("staffId", staffId)
        ).andExpect(status().isOk)

        verify(staffAuditPort).log(argThat { entry ->
            entry.staffId == staffId &&
                    entry.venueId == venueId &&
                    entry.action.name == "EVENT_CREATE" &&
                    entry.subjectType == "event" &&
                    entry.subjectId == event.id.toString() &&
                    entry.outcome.name == "SUCCESS"
        })
    }

    @Test
    @WithMockUser(roles = ["STAFF"])
    fun `update event records audit`() {
        val request = EventRequest(title = "Updated", currency = "AMD")
        val event = event()
        whenever(eventService.updateEvent(eq(eventId), eq(venueId), any(), anyOrNull(), anyOrNull())).thenReturn(event)
        whenever(eventMapper.toResponse(eq(event), any(), anyOrNull(), any(), anyOrNull(), any())).thenReturn(
            eventResponse(event.id)
        )
        whenever(venueApi.getVenueName(eq(venueId))).thenReturn("Venue")
        whenever(platformSubscriptionApi.getEventSubscriptions(eq(event.id))).thenReturn(emptyList())

        val dataPart =
            MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(request))

        mockMvc.perform(
            multipart("/api/v1/staff/venues/$venueId/events/$eventId")
                .file(dataPart)
                .with(csrf())
                .requestAttr("staffId", staffId)
                .with { it.method = "PUT"; it }
        ).andExpect(status().isOk)

        verify(staffAuditPort).log(argThat { entry ->
            entry.staffId == staffId &&
                    entry.venueId == venueId &&
                    entry.action.name == "EVENT_UPDATE" &&
                    entry.subjectType == "event" &&
                    entry.subjectId == eventId.toString() &&
                    entry.outcome.name == "SUCCESS"
        })
    }

    @Test
    @WithMockUser(roles = ["STAFF"])
    fun `delete event records audit`() {
        mockMvc.perform(
            delete("/api/v1/staff/venues/$venueId/events/$eventId")
                .with(csrf())
                .requestAttr("staffId", staffId)
        ).andExpect(status().isOk)

        verify(staffAuditPort).log(argThat { entry ->
            entry.staffId == staffId &&
                    entry.venueId == venueId &&
                    entry.action.name == "EVENT_DELETE" &&
                    entry.subjectType == "event" &&
                    entry.subjectId == eventId.toString() &&
                    entry.outcome.name == "SUCCESS"
        })
    }

    @Test
    @WithMockUser(roles = ["STAFF"])
    fun `create price template records audit`() {
        val request = PriceTemplateRequest(templateName = "VIP", price = MoneyAmount(BigDecimal("100.00"), "AMD"))
        val event = event()
        val template = EventPriceTemplate(event, request.templateName, request.price.amount, request.color, false)
        whenever(eventService.createPriceTemplate(eq(eventId), eq(venueId), eq(request))).thenReturn(template)
        whenever(eventMapper.toPriceTemplateResponse(eq(template))).thenReturn(priceTemplateResponse(template.id))

        mockMvc.perform(
            post("/api/v1/staff/venues/$venueId/events/$eventId/price-templates")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request))
                .requestAttr("staffId", staffId)
        ).andExpect(status().isOk)

        verify(staffAuditPort).log(argThat { entry ->
            entry.staffId == staffId &&
                    entry.venueId == venueId &&
                    entry.action.name == "EVENT_PRICE_TEMPLATE_CREATE" &&
                    entry.subjectType == "event" &&
                    entry.subjectId == eventId.toString() &&
                    entry.outcome.name == "SUCCESS" &&
                    entry.metadata["eventId"] == eventId.toString() &&
                    entry.metadata["resultId"] == template.id.toString()
        })
    }

    @Test
    @WithMockUser(roles = ["STAFF"])
    fun `assign session pricing records audit with counts`() {
        val request = AssignPriceTemplateRequest(
            templateId = templateId,
            seatIds = listOf(1L, 2L),
            tableIds = listOf(3L),
            gaIds = listOf(4L)
        )

        mockMvc.perform(
            put("/api/v1/staff/venues/$venueId/events/$eventId/sessions/$sessionId/pricing")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request))
                .requestAttr("staffId", staffId)
        ).andExpect(status().isOk)

        verify(staffAuditPort).log(argThat { entry ->
            entry.staffId == staffId &&
                    entry.venueId == venueId &&
                    entry.action.name == "EVENT_SESSION_PRICING_ASSIGN" &&
                    entry.subjectType == "event_session" &&
                    entry.subjectId == sessionId.toString() &&
                    entry.outcome.name == "SUCCESS" &&
                    entry.metadata["templateId"] == templateId.toString() &&
                    entry.metadata["seatCount"] == 2 &&
                    entry.metadata["tableCount"] == 1
        })
    }

    @Test
    @WithMockUser(roles = ["STAFF"])
    fun `assign event pricing records audit`() {
        val request = EventPricingAssignRequest(
            templateId = templateId,
            seatIds = listOf(1L),
            tableIds = emptyList(),
            gaIds = listOf(9L, 10L)
        )

        mockMvc.perform(
            put("/api/v1/staff/venues/$venueId/events/$eventId/pricing")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request))
                .requestAttr("staffId", staffId)
        ).andExpect(status().isOk)

        verify(staffAuditPort).log(argThat { entry ->
            entry.staffId == staffId &&
                    entry.venueId == venueId &&
                    entry.action.name == "EVENT_PRICING_ASSIGN" &&
                    entry.subjectType == "event" &&
                    entry.subjectId == eventId.toString() &&
                    entry.outcome.name == "SUCCESS" &&
                    entry.metadata["templateId"] == templateId.toString() &&
                    entry.metadata["seatCount"] == 1
        })
    }

    @Test
    @WithMockUser(roles = ["STAFF"])
    fun `change event status records audit`() {
        val request = EventStatusChangeRequest(status = EventStatus.PUBLISHED, reason = "Ready")
        val event = event(status = EventStatus.PUBLISHED)
        whenever(eventStatusService.changeEventStatus(eventId, venueId, request.status, request.reason)).thenReturn(
            event
        )
        whenever(
            eventStatusService.getAllowedEventTransitions(
                eventId,
                venueId
            )
        ).thenReturn(setOf(EventStatus.ARCHIVED))

        mockMvc.perform(
            put("/api/v1/staff/venues/$venueId/events/$eventId/status")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request))
                .requestAttr("staffId", staffId)
        ).andExpect(status().isOk)

        verify(staffAuditPort).log(argThat { entry ->
            entry.staffId == staffId &&
                    entry.venueId == venueId &&
                    entry.action.name == "EVENT_STATUS_CHANGE" &&
                    entry.subjectType == "event" &&
                    entry.subjectId == eventId.toString() &&
                    entry.outcome.name == "SUCCESS" &&
                    entry.metadata["targetStatus"] == request.status.name &&
                    entry.metadata["reason"] == request.reason
        })
    }

    @Test
    @WithMockUser(roles = ["STAFF"])
    fun `close seats records audit`() {
        val request = VenueEventController.SeatIdsRequest(seatIds = listOf(1L, 2L))
        whenever(eventApi.getEventSessionInfo(eq(sessionId))).thenReturn(eventSessionDto())
        whenever(eventApi.closeSeats(eq(sessionId), eq(request.seatIds))).thenReturn(2)

        mockMvc.perform(
            put("/api/v1/staff/venues/$venueId/events/$eventId/sessions/$sessionId/seats/close")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request))
                .requestAttr("staffId", staffId)
        ).andExpect(status().isOk)

        verify(staffAuditPort).log(argThat { entry ->
            entry.staffId == staffId &&
                    entry.venueId == venueId &&
                    entry.action.name == "EVENT_SEATS_CLOSE" &&
                    entry.subjectType == "event_session" &&
                    entry.subjectId == sessionId.toString() &&
                    entry.outcome.name == "SUCCESS" &&
                    entry.metadata["seatCount"] == 2 &&
                    entry.metadata["affectedCount"] == 2
        })
    }

    @Test
    @WithMockUser(roles = ["STAFF"])
    fun `open tables records audit`() {
        val request = VenueEventController.TableIdsRequest(tableIds = listOf(9L))
        whenever(eventApi.getEventSessionInfo(eq(sessionId))).thenReturn(eventSessionDto())
        whenever(eventApi.reopenTables(eq(sessionId), eq(request.tableIds))).thenReturn(1)

        mockMvc.perform(
            put("/api/v1/staff/venues/$venueId/events/$eventId/sessions/$sessionId/tables/open")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request))
                .requestAttr("staffId", staffId)
        ).andExpect(status().isOk)

        verify(staffAuditPort).log(argThat { entry ->
            entry.staffId == staffId &&
                    entry.venueId == venueId &&
                    entry.action.name == "EVENT_TABLES_OPEN" &&
                    entry.subjectType == "event_session" &&
                    entry.subjectId == sessionId.toString() &&
                    entry.outcome.name == "SUCCESS" &&
                    entry.metadata["tableCount"] == 1 &&
                    entry.metadata["affectedCount"] == 1
        })
    }

    @Test
    @WithMockUser(roles = ["STAFF"])
    fun `change session status records audit`() {
        val request = SessionStatusChangeRequest(status = SessionStatus.PAUSED, reason = "Maintenance")
        val session = session(status = request.status)
        whenever(eventStatusService.changeSessionStatus(sessionId, venueId, request.status, request.reason)).thenReturn(
            session
        )
        whenever(
            eventStatusService.getAllowedSessionTransitions(
                sessionId,
                venueId
            )
        ).thenReturn(setOf(SessionStatus.CANCELLED))

        mockMvc.perform(
            put("/api/v1/staff/venues/$venueId/events/$eventId/sessions/$sessionId/status")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request))
                .requestAttr("staffId", staffId)
        ).andExpect(status().isOk)

        verify(staffAuditPort).log(argThat { entry ->
            entry.staffId == staffId &&
                    entry.venueId == venueId &&
                    entry.action.name == "EVENT_SESSION_STATUS_CHANGE" &&
                    entry.subjectType == "event_session" &&
                    entry.subjectId == sessionId.toString() &&
                    entry.outcome.name == "SUCCESS" &&
                    entry.metadata["targetStatus"] == request.status.name &&
                    entry.metadata["reason"] == request.reason
        })
    }

    private fun event(status: EventStatus = EventStatus.DRAFT): Event = object : Event(
        title = "Sample",
        venueId = venueId
    ) {
        init {
            this.status = status
        }
    }

    private fun session(status: SessionStatus = SessionStatus.ON_SALE): EventSession = object : EventSession(
        event = event(EventStatus.PUBLISHED),
        startTime = Instant.EPOCH,
        endTime = Instant.EPOCH.plusSeconds(3600)
    ) {
        init {
            this.status = status
        }
    }

    private fun eventResponse(id: UUID): EventResponse = EventResponse(
        id = id,
        title = "Sample",
        description = null,
        imgUrl = null,
        secondaryImgUrls = emptyList(),
        venueId = venueId,
        venueName = "Venue",
        location = null,
        latitude = null,
        longitude = null,
        categoryCode = null,
        categoryName = null,
        tags = emptySet(),
        priceRange = null,
        currency = "AMD",
        seatingChartName = null,
        status = EventStatus.DRAFT,
        createdAt = Instant.EPOCH.toString(),
        lastModifiedAt = Instant.EPOCH.toString()
    )

    private fun priceTemplateResponse(id: UUID): PriceTemplateResponse = PriceTemplateResponse(
        id = id,
        templateName = "VIP",
        color = "#FFFFFF",
        price = MoneyAmount(BigDecimal("100.00"), "AMD"),
        isRemovable = true
    )

    private fun eventSessionDto(): EventSessionDto = EventSessionDto(
        sessionId = sessionId,
        eventId = eventId,
        venueId = venueId,
        seatingChartId = UUID.randomUUID(),
        eventTitle = "Sample",
        eventDescription = null,
        currency = "AMD",
        startTime = Instant.EPOCH,
        endTime = Instant.EPOCH.plusSeconds(3600)
    )
}
