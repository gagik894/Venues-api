package app.venues.event.service

import app.venues.common.exception.VenuesException
import app.venues.event.api.dto.*
import app.venues.event.api.mapper.EventMapper
import app.venues.event.domain.Event
import app.venues.event.domain.EventPriceTemplate
import app.venues.event.domain.EventStatus
import app.venues.event.domain.EventTranslation
import app.venues.event.repository.EventCategoryRepository
import app.venues.event.repository.EventRepository
import app.venues.event.repository.EventSessionRepository
import app.venues.seating.api.SeatingApi
import app.venues.venue.api.VenueApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

/**
 * Service for event management operations.
 *
 * Uses API interfaces (VenueApi, SeatingApi) for cross-module communication,
 * enforcing Hexagonal Architecture boundaries.
 *
 * Handles:
 * - Event CRUD operations
 * - Session management
 * - Translation management
 * - Price template management
 * - Search and filtering
 */
@Service
@Transactional
class EventService(
    private val eventRepository: EventRepository,
    private val eventSessionRepository: EventSessionRepository,
    private val eventCategoryRepository: EventCategoryRepository,
    private val eventSessionService: EventSessionService,
    private val eventPriceService: EventPriceService,
    private val venueApi: VenueApi,
    private val seatingApi: SeatingApi,
    private val imageStorageService: ImageStorageService,
    private val eventMapper: EventMapper,
    private val eventRevalidationService: EventRevalidationService
) {
    private val logger = KotlinLogging.logger {}
    private val staffVisibleStatuses: Set<EventStatus> =
        EventStatus.entries.filterNot { it == EventStatus.DELETED }.toSet()

    // ===========================================
    // EVENT CRUD
    // ===========================================

    /**
     * Create a new event for a venue.
     *
     * @param venueId The ID of the venue creating the event.
     * @param request The event creation request data.
     * @param image Optional main image file.
     * @param secondaryImages Optional list of secondary image files.
     * @return The created Event entity.
     * @throws VenuesException.ResourceNotFound If venue, seating chart, or category is not found.
     */
    fun createEvent(
        venueId: UUID,
        request: EventRequest,
        image: org.springframework.web.multipart.MultipartFile? = null,
        secondaryImages: List<org.springframework.web.multipart.MultipartFile>? = null
    ): Event {
        logger.debug { "Creating event for venue: $venueId" }

        if (!venueApi.venueExists(venueId)) {
            throw VenuesException.ResourceNotFound("Venue not found with ID: $venueId")
        }

        // Fallback to venue location if not provided
        var location = request.location
        var latitude = request.latitude
        var longitude = request.longitude

        if (location == null || latitude == null || longitude == null) {
            val venueInfo = venueApi.getVenueBasicInfo(venueId)
            if (venueInfo != null) {
                if (location == null) location = venueInfo.address
                if (latitude == null) latitude = venueInfo.latitude
                if (longitude == null) longitude = venueInfo.longitude
            }
        }

        if (request.seatingChartId != null) {
            seatingApi.getSeatingChartName(request.seatingChartId)
        }

        val category = request.categoryCode?.let { code ->
            eventCategoryRepository.findByCode(code)
                ?: throw VenuesException.ResourceNotFound("Category not found with code: $code")
        }

        // Handle image uploads
        val mainImgUrl = if (image != null && !image.isEmpty) {
            imageStorageService.store(image)
        } else {
            request.imgUrl
        }

        val event = Event(
            title = request.title,
            description = request.description,
            imgUrl = mainImgUrl,
            venueId = venueId,
            location = location,
            latitude = latitude,
            longitude = longitude,
            priceRange = null,
            currency = request.currency,
            seatingChartId = request.seatingChartId,
            category = category
        )

        // Handle secondary images
        event.secondaryImgUrls.addAll(request.secondaryImgUrls)
        secondaryImages?.forEach { file ->
            if (!file.isEmpty) {
                event.secondaryImgUrls.add(imageStorageService.store(file))
            }
        }
        event.tags.addAll(request.tags)

        // Delegate Sessions
        eventSessionService.updateSessions(event, request.sessions)

        // Update session timestamps for sorting/filtering
        event.firstSessionStart = event.sessions.minOfOrNull { it.startTime }
        event.lastSessionEnd = event.sessions.maxOfOrNull { it.endTime }

        // Create translations
        updateTranslationsCollection(event, request.translations)

        val savedEvent = eventRepository.save(event)

        // Generate configs for sessions now that event is saved
        eventSessionService.generateConfigsForNewSessions(savedEvent)
        
        logger.info { "Event created successfully: ID=${savedEvent.id}" }

        return savedEvent
    }

    /**
     * Get event by ID.
     *
     * @param id The ID of the event to retrieve.
     * @return The Event entity.
     * @throws VenuesException.ResourceNotFound If event is not found.
     */
    @Transactional(readOnly = true)
    fun getEventById(id: UUID): Event {
        logger.debug { "Fetching event by ID: $id" }

        return eventRepository.findById(id)
            .orElseThrow { VenuesException.ResourceNotFound("Event not found with ID: $id") }
    }

    @Transactional(readOnly = true)
    fun getPublishedEventById(id: UUID): Event {
        val event = getEventById(id)
        if (event.status != EventStatus.PUBLISHED) {
            throw VenuesException.ResourceNotFound("Event not found with ID: $id")
        }
        return event
    }

    @Transactional(readOnly = true)
    fun getEventForVenueStaff(eventId: UUID, venueId: UUID): Event {
        val event = getEventById(eventId)
        if (event.venueId != venueId) {
            throw VenuesException.AuthorizationFailure("Event does not belong to this venue")
        }
        if (event.status == EventStatus.DELETED) {
            throw VenuesException.ResourceNotFound("Event not found with ID: $eventId")
        }
        return event
    }

    /**
     * Update event.
     *
     * @param eventId The ID of the event to update.
     * @param venueId The ID of the venue requesting the update (for ownership check).
     * @param request The event update request data.
     * @param image Optional new main image file.
     * @param secondaryImages Optional list of new secondary image files.
     * @return The updated Event entity.
     * @throws VenuesException.ResourceNotFound If event is not found.
     * @throws VenuesException.AuthorizationFailure If venueId does not match event owner.
     * @throws VenuesException.ValidationFailure If event is not in editable state.
     */
    fun updateEvent(
        eventId: UUID,
        venueId: UUID,
        request: EventRequest,
        image: org.springframework.web.multipart.MultipartFile? = null,
        secondaryImages: List<org.springframework.web.multipart.MultipartFile>? = null
    ): Event {
        logger.debug { "Updating event: $eventId for venue: $venueId" }

        val event = eventRepository.findById(eventId)
            .orElseThrow { VenuesException.ResourceNotFound("Event not found with ID: $eventId") }

        // Verify ownership
        if (event.venueId != venueId) {
            throw VenuesException.AuthorizationFailure("You can only update your own events")
        }

        // Check if event is editable
        if (!event.isEditable()) {
            throw VenuesException.ValidationFailure("Event cannot be edited in current status: ${event.status}")
        }

        if (request.seatingChartId != null) {
            seatingApi.getSeatingChartName(request.seatingChartId)
        }

        // Fallback to venue location if not provided
        var location = request.location
        var latitude = request.latitude
        var longitude = request.longitude

        if (location == null || latitude == null || longitude == null) {
            val venueInfo = venueApi.getVenueBasicInfo(venueId)
            if (venueInfo != null) {
                if (location == null) location = venueInfo.address
                if (latitude == null) latitude = venueInfo.latitude
                if (longitude == null) longitude = venueInfo.longitude
            }
        }

        // Update fields
        event.title = request.title
        event.description = request.description

        // Update main image if provided, otherwise keep existing or use URL from request
        if (image != null && !image.isEmpty) {
            event.imgUrl = imageStorageService.store(image)
        } else if (request.imgUrl != null) {
            event.imgUrl = request.imgUrl
        }

        event.location = location
        event.latitude = latitude
        event.longitude = longitude
        event.currency = request.currency
        event.seatingChartId = request.seatingChartId

        if (request.categoryCode != null) {
            event.category = eventCategoryRepository.findByCode(request.categoryCode)
                ?: throw VenuesException.ResourceNotFound("Category not found with code: ${request.categoryCode}")
        } else {
            event.category = null
        }

        // Update collections
        event.secondaryImgUrls.clear()
        event.secondaryImgUrls.addAll(request.secondaryImgUrls)
        secondaryImages?.forEach { file ->
            if (!file.isEmpty) {
                event.secondaryImgUrls.add(imageStorageService.store(file))
            }
        }
        
        event.tags.clear()
        event.tags.addAll(request.tags)

        // Delegate Sessions
        eventSessionService.updateSessions(event, request.sessions)

        // Update session timestamps for sorting/filtering
        event.firstSessionStart = event.sessions.minOfOrNull { it.startTime }
        event.lastSessionEnd = event.sessions.maxOfOrNull { it.endTime }

        // Update translations
        updateTranslationsCollection(event, request.translations)

        val savedEvent = eventRepository.save(event)

        // Generate configs for any new sessions
        eventSessionService.generateConfigsForNewSessions(savedEvent)

        eventRevalidationService.onEventUpdated(savedEvent, includeDetail = true, reason = "event-updated")
        
        logger.info { "Event updated successfully: $eventId" }

        return savedEvent
    }

    /**
     * Delete event.
     * Performs a soft delete if the event has any sales or is published.
     * Hard deletes only if it's a draft with no sales.
     */
    fun deleteEvent(eventId: UUID, venueId: UUID) {
        logger.debug { "Deleting event: $eventId" }

        val event = eventRepository.findById(eventId)
            .orElseThrow { VenuesException.ResourceNotFound("Event not found with ID: $eventId") }

        val previousStatus = event.status

        // Verify ownership
        if (event.venueId != venueId) {
            throw VenuesException.AuthorizationFailure("You can only delete your own events")
        }

        // Check for any sales across all sessions
        val hasSales = event.sessions.any { it.ticketsSold > 0 }

        if (hasSales) {
            // Soft delete
            logger.info { "Soft deleting event $eventId due to existing sales" }
            event.markAsDeleted()
            eventRepository.save(event)
        } else {
            // Hard delete
            eventRepository.delete(event)
            logger.info { "Event hard deleted successfully: $eventId" }
        }

        if (previousStatus in setOf(EventStatus.PUBLISHED, EventStatus.SUSPENDED, EventStatus.ARCHIVED)) {
            eventRevalidationService.onUnpublish(event, "event-deleted")
        }
    }

    // ===========================================
    // EVENT LISTING & SEARCH
    // ===========================================

    /**
     * Get all publicly visible events as summaries.
     *
     * @param pageable Pagination information.
     * @param language Optional language code for translations.
     * @return Page of EventSummaryResponse DTOs.
     */
    @Transactional(readOnly = true)
    fun getAllEventSummaries(pageable: Pageable, language: String?): Page<EventSummaryResponse> {
        logger.debug { "Fetching all publicly visible events (summary)" }
        val events = eventRepository.findByStatus(EventStatus.PUBLISHED, pageable)
        return mapToSummary(events, language)
    }

    /**
     * Search events by title as summaries.
     *
     * @param searchTerm The term to search for in event titles.
     * @param pageable Pagination information.
     * @param language Optional language code for translations.
     * @return Page of EventSummaryResponse DTOs.
     */
    @Transactional(readOnly = true)
    fun searchEventSummaries(searchTerm: String, pageable: Pageable, language: String?): Page<EventSummaryResponse> {
        logger.debug { "Searching events (summary): $searchTerm" }
        val events = eventRepository.searchByTitle(searchTerm, EventStatus.PUBLISHED, pageable)
        return mapToSummary(events, language)
    }

    /**
     * Get events by venue as summaries.
     *
     * @param venueId The ID of the venue.
     * @param pageable Pagination information.
     * @param language Optional language code for translations.
     * @return Page of EventSummaryResponse DTOs.
     */
    @Transactional(readOnly = true)
    fun getEventSummariesByVenue(venueId: UUID, pageable: Pageable, language: String?): Page<EventSummaryResponse> {
        logger.debug { "Fetching events for venue (summary): $venueId" }
        val events = eventRepository.findByVenueIdAndStatus(venueId, EventStatus.PUBLISHED, pageable)
        return mapToSummary(events, language)
    }

    @Transactional(readOnly = true)
    fun getStaffEventSummariesByVenue(
        venueId: UUID,
        pageable: Pageable,
        statuses: Set<EventStatus>?,
        language: String?
    ): Page<EventSummaryResponse> {
        val effectiveStatuses = statuses?.takeIf { it.isNotEmpty() } ?: staffVisibleStatuses
        val resolvedStatuses =
            effectiveStatuses.filterNot { it == EventStatus.DELETED }.toSet().ifEmpty { staffVisibleStatuses }
        val events = eventRepository.findByVenueIdAndStatusIn(venueId, resolvedStatuses, pageable)
        return mapToSummary(events, language)
    }

    /**
     * Get events by category as summaries.
     *
     * @param categoryId The ID of the category.
     * @param pageable Pagination information.
     * @param language Optional language code for translations.
     * @return Page of EventSummaryResponse DTOs.
     */
    @Transactional(readOnly = true)
    fun getEventSummariesByCategory(
        categoryId: Long,
        pageable: Pageable,
        language: String?
    ): Page<EventSummaryResponse> {
        logger.debug { "Fetching events for category (summary): $categoryId" }
        val events = eventRepository.findByCategoryIdAndStatus(categoryId, EventStatus.PUBLISHED, pageable)
        return mapToSummary(events, language)
    }

    /**
     * Get events by tag as summaries.
     *
     * @param tag The tag to filter by.
     * @param pageable Pagination information.
     * @param language Optional language code for translations.
     * @return Page of EventSummaryResponse DTOs.
     */
    @Transactional(readOnly = true)
    fun getEventSummariesByTag(tag: String, pageable: Pageable, language: String?): Page<EventSummaryResponse> {
        logger.debug { "Fetching events for tag (summary): $tag" }
        val events = eventRepository.findByTag(tag, EventStatus.PUBLISHED, pageable)
        return mapToSummary(events, language)
    }

    private fun mapToSummary(events: Page<Event>, language: String?): Page<EventSummaryResponse> {
        val venueIds = events.content.map { it.venueId }.toSet()
        val venueNames = venueApi.getVenueNamesBatch(venueIds, language)

        return events.map { event ->
            // Fetch category name within transaction scope
            val categoryName = event.category?.getName(language ?: "en")

            // Fetch next session time within transaction scope (uses @BatchSize)
            val nextSession = event.sessions.firstOrNull { it.startTime.isAfter(Instant.now()) }
                ?: event.sessions.firstOrNull()

            eventMapper.toSummaryResponse(
                event = event,
                venueName = venueNames[event.venueId] ?: "Unknown",
                categoryName = categoryName,
                startDateTime = nextSession?.startTime?.toString(),
                language = language
            )
        }
    }

    /**
     * Get all publicly visible events.
     *
     * @param pageable Pagination information.
     * @return Page of Event entities.
     */
    @Transactional(readOnly = true)
    fun getAllEvents(pageable: Pageable): Page<Event> {
        logger.debug { "Fetching all publicly visible events" }
        return eventRepository.findByStatus(EventStatus.PUBLISHED, pageable)
    }

    /**
     * Search events by title.
     *
     * @param searchTerm The term to search for in event titles.
     * @param pageable Pagination information.
     * @return Page of Event entities.
     */
    @Transactional(readOnly = true)
    fun searchEvents(searchTerm: String, pageable: Pageable): Page<Event> {
        logger.debug { "Searching events: $searchTerm" }
        return eventRepository.searchByTitle(searchTerm, EventStatus.PUBLISHED, pageable)
    }

    /**
     * Get events by venue.
     *
     * @param venueId The ID of the venue.
     * @param pageable Pagination information.
     * @return Page of Event entities.
     */
    @Transactional(readOnly = true)
    fun getEventsByVenue(venueId: UUID, pageable: Pageable): Page<Event> {
        logger.debug { "Fetching events for venue: $venueId" }
        return eventRepository.findByVenueIdAndStatus(venueId, EventStatus.PUBLISHED, pageable)
    }

    /**
     * Get events by category.
     *
     * @param categoryId The ID of the category.
     * @param pageable Pagination information.
     * @return Page of Event entities.
     */
    @Transactional(readOnly = true)
    fun getEventsByCategory(categoryId: Long, pageable: Pageable): Page<Event> {
        logger.debug { "Fetching events for category: $categoryId" }
        return eventRepository.findByCategoryIdAndStatus(categoryId, EventStatus.PUBLISHED, pageable)
    }

    /**
     * Get events by tag.
     *
     * @param tag The tag to filter by.
     * @param pageable Pagination information.
     * @return Page of Event entities.
     */
    @Transactional(readOnly = true)
    fun getEventsByTag(tag: String, pageable: Pageable): Page<Event> {
        logger.debug { "Fetching events for tag: $tag" }
        return eventRepository.findByTag(tag, EventStatus.PUBLISHED, pageable)
    }

    // ===========================================
    // PRICE TEMPLATE MANAGEMENT
    // ===========================================

    /**
     * Get price templates for an event.
     *
     * @param eventId The ID of the event.
     * @return List of EventPriceTemplate entities.
     * @throws VenuesException.ResourceNotFound If event is not found.
     */
    fun getPriceTemplates(eventId: UUID): List<EventPriceTemplate> {
        val event = eventRepository.findById(eventId)
            .orElseThrow { VenuesException.ResourceNotFound("Event not found: $eventId") }

        return event.priceTemplates.toList()
    }

    /**
     * Create a price template for an event.
     *
     * @param eventId The ID of the event.
     * @param venueId The ID of the venue (for ownership check).
     * @param request The price template creation request.
     * @return The created EventPriceTemplate entity.
     * @throws VenuesException.ResourceNotFound If event is not found.
     * @throws VenuesException.AuthorizationFailure If venueId does not match event owner.
     */
    fun createPriceTemplate(eventId: UUID, venueId: UUID, request: PriceTemplateRequest): EventPriceTemplate {
        val event = eventRepository.findById(eventId)
            .orElseThrow { VenuesException.ResourceNotFound("Event not found: $eventId") }

        if (event.venueId != venueId) {
            throw VenuesException.AuthorizationFailure("Not authorized")
        }

        val template = eventPriceService.createTemplate(event, request)
        eventRepository.save(event)

        eventRevalidationService.onEventUpdated(event, includeDetail = true, reason = "event-pricing-created")

        return template
    }

    /**
     * Update a price template.
     *
     * @param eventId The ID of the event.
     * @param venueId The ID of the venue.
     * @param templateId The ID of the template to update.
     * @param request The update request.
     * @return The updated EventPriceTemplate entity.
     */
    fun updatePriceTemplate(
        eventId: UUID,
        venueId: UUID,
        templateId: UUID,
        request: PriceTemplateRequest
    ): EventPriceTemplate {
        val event = eventRepository.findById(eventId)
            .orElseThrow { VenuesException.ResourceNotFound("Event not found: $eventId") }

        if (event.venueId != venueId) {
            throw VenuesException.AuthorizationFailure("Not authorized")
        }

        val template = eventPriceService.updateTemplate(event, templateId, request)
        eventRepository.save(event)

        eventRevalidationService.onEventUpdated(event, includeDetail = true, reason = "event-pricing-updated")

        return template
    }

    /**
     * Delete a price template.
     *
     * @param eventId The ID of the event.
     * @param venueId The ID of the venue.
     * @param templateId The ID of the template to delete.
     */
    fun deletePriceTemplate(eventId: UUID, venueId: UUID, templateId: UUID) {
        val event = eventRepository.findById(eventId)
            .orElseThrow { VenuesException.ResourceNotFound("Event not found: $eventId") }

        if (event.venueId != venueId) {
            throw VenuesException.AuthorizationFailure("Not authorized")
        }

        eventPriceService.deleteTemplate(event, templateId)
        eventRepository.save(event)

        eventRevalidationService.onEventUpdated(event, includeDetail = true, reason = "event-pricing-deleted")
    }

    private fun updateTranslationsCollection(event: Event, translationRequests: List<EventTranslationRequest>) {
        val existingTranslationsMap = event.translations.associateBy { it.language }
        val requestLanguages = translationRequests.map { it.language.lowercase() }.toSet()

        // Remove deleted translations
        event.translations.removeIf { it.language !in requestLanguages }

        translationRequests.forEach { request ->
            val lang = request.language.lowercase()
            if (existingTranslationsMap.containsKey(lang)) {
                // Update existing
                val translation = existingTranslationsMap[lang]!!
                translation.title = request.title
                translation.description = request.description
            } else {
                // Create new
                val translation = EventTranslation(
                    event = event,
                    language = lang,
                    title = request.title,
                    description = request.description
                )
                event.addTranslation(translation)
            }
        }
    }

    // ===========================================
    // EVENT SESSIONS (Read Operations)
    // ===========================================

    /**
     * Get sessions for an event.
     *
     * @param eventId The ID of the event.
     * @param pageable Pagination information.
     * @return Page of EventSessionResponse DTOs.
     */
    @Transactional(readOnly = true)
    fun getEventSessions(eventId: UUID, pageable: Pageable): Page<EventSessionResponse> {
        logger.debug { "Fetching sessions for event: $eventId" }
        val sessions = eventSessionRepository.findByEventId(eventId, pageable)
        return sessions.map { session ->
            eventMapper.toSessionResponse(session)
        }
    }

    /**
     * Get upcoming bookable sessions for an event.
     *
     * @param eventId The ID of the event.
     * @return List of EventSessionResponse DTOs.
     */
    @Transactional(readOnly = true)
    fun getBookableSessions(eventId: UUID): List<EventSessionResponse> {
        logger.debug { "Fetching bookable sessions for event: $eventId" }
        val sessions = eventSessionRepository.findBookableSessions(eventId, Instant.now())
        return sessions.map { session ->
            eventMapper.toSessionResponse(session)
        }
    }

    /**
     * Get translations for an event.
     *
     * @param eventId The ID of the event.
     * @return List of EventTranslation entities.
     */
    @Transactional(readOnly = true)
    fun getTranslations(eventId: UUID): List<EventTranslation> {
        logger.debug { "Fetching translations for event: $eventId" }

        val event = eventRepository.findById(eventId)
            .orElseThrow { VenuesException.ResourceNotFound("Event not found with ID: $eventId") }

        return event.translations.toList()
    }

    // ===========================================
    // SEATING & PRICING
    // ===========================================

    /**
     * Batch assign price template to seats/tables in a session.
     */
    fun assignPriceTemplate(
        eventId: UUID,
        sessionId: UUID,
        venueId: UUID,
        templateId: UUID?,
        seatIds: List<Long>,
        tableIds: List<Long>,
        gaIds: List<Long>
    ) {
        val event = eventRepository.findById(eventId)
            .orElseThrow { VenuesException.ResourceNotFound("Event not found: $eventId") }

        if (event.venueId != venueId) {
            throw VenuesException.AuthorizationFailure("Not authorized")
        }

        // Verify session belongs to event
        event.sessions.find { it.id == sessionId }
            ?: throw VenuesException.ResourceNotFound("Session not found in event")

        val template = if (templateId != null) {
            event.priceTemplates.find { it.id == templateId }
                ?: throw VenuesException.ResourceNotFound("Price template not found: $templateId")
        } else null

        if (seatIds.isNotEmpty()) {
            eventSessionService.assignPriceTemplateToSeats(sessionId, template, seatIds)
        }
        if (tableIds.isNotEmpty()) {
            eventSessionService.assignPriceTemplateToTables(sessionId, template, tableIds)
        }
        if (gaIds.isNotEmpty()) {
            gaIds.forEach { gaId ->
                eventSessionService.assignPriceTemplateToGa(sessionId, template, gaId)
            }
        }

        eventRevalidationService.onEventUpdated(event, includeDetail = true, reason = "event-pricing-assigned")
    }
}

