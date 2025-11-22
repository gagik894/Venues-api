package app.venues.event.service

import app.venues.common.exception.VenuesException
import app.venues.event.api.dto.EventRequest
import app.venues.event.api.dto.EventTranslationRequest
import app.venues.event.api.dto.PriceTemplateRequest
import app.venues.event.domain.*
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
    private val seatingApi: SeatingApi
) {
    private val logger = KotlinLogging.logger {}

    // ===========================================
    // EVENT CRUD
    // ===========================================

    /**
     * Create a new event for a venue.
     *
     * @param venueId The ID of the venue creating the event.
     * @param request The event creation request data.
     * @return The created Event entity.
     * @throws VenuesException.ResourceNotFound If venue, seating chart, or category is not found.
     */
    fun createEvent(venueId: UUID, request: EventRequest): Event {
        logger.debug { "Creating event for venue: $venueId" }

        if (!venueApi.venueExists(venueId)) {
            throw VenuesException.ResourceNotFound("Venue not found with ID: $venueId")
        }

        if (request.seatingChartId != null) {
            seatingApi.getSeatingChartName(request.seatingChartId)
        }

        val category = request.categoryCode?.let { code ->
            eventCategoryRepository.findByCode(code)
                ?: throw VenuesException.ResourceNotFound("Category not found with code: $code")
        }

        val event = Event(
            title = request.title,
            description = request.description,
            imgUrl = request.imgUrl,
            venueId = venueId,
            location = request.location,
            latitude = request.latitude,
            longitude = request.longitude,
            priceRange = null,
            currency = request.currency,
            seatingChartId = request.seatingChartId,
            category = category
        )

        event.secondaryImgUrls.addAll(request.secondaryImgUrls)
        event.tags.addAll(request.tags)

        // Delegate Sessions
        eventSessionService.updateSessions(event, request.sessions)

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

    /**
     * Update event.
     *
     * @param eventId The ID of the event to update.
     * @param venueId The ID of the venue requesting the update (for ownership check).
     * @param request The event update request data.
     * @return The updated Event entity.
     * @throws VenuesException.ResourceNotFound If event is not found.
     * @throws VenuesException.AuthorizationFailure If venueId does not match event owner.
     * @throws VenuesException.ValidationFailure If event is not in editable state.
     */
    fun updateEvent(eventId: UUID, venueId: UUID, request: EventRequest): Event {
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

        // Update fields
        event.title = request.title
        event.description = request.description
        event.imgUrl = request.imgUrl
        event.location = request.location
        event.latitude = request.latitude
        event.longitude = request.longitude
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
        event.tags.clear()
        event.tags.addAll(request.tags)

        // Delegate Sessions
        eventSessionService.updateSessions(event, request.sessions)

        // Update translations
        updateTranslationsCollection(event, request.translations)

        val savedEvent = eventRepository.save(event)

        // Generate configs for any new sessions
        eventSessionService.generateConfigsForNewSessions(savedEvent)
        
        logger.info { "Event updated successfully: $eventId" }

        return savedEvent
    }

    /**
     * Delete event.
     */
    fun deleteEvent(eventId: UUID, venueId: UUID) {
        logger.debug { "Deleting event: $eventId" }

        val event = eventRepository.findById(eventId)
            .orElseThrow { VenuesException.ResourceNotFound("Event not found with ID: $eventId") }

        // Verify ownership
        if (event.venueId != venueId) {
            throw VenuesException.AuthorizationFailure("You can only delete your own events")
        }

        eventRepository.delete(event)
        logger.info { "Event deleted successfully: $eventId" }
    }

    // ===========================================
    // EVENT LISTING & SEARCH
    // ===========================================

    /**
     * Get all publicly visible events.
     *
     * @param pageable Pagination information.
     * @return Page of Event entities.
     */
    @Transactional(readOnly = true)
    fun getAllEvents(pageable: Pageable): Page<Event> {
        logger.debug { "Fetching all publicly visible events" }
        return eventRepository.findByStatus(EventStatus.UPCOMING, pageable)
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
        return eventRepository.searchByTitle(searchTerm, EventStatus.UPCOMING, pageable)
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
        return eventRepository.findByVenueIdAndStatus(venueId, EventStatus.UPCOMING, pageable)
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
        return eventRepository.findByCategoryIdAndStatus(categoryId, EventStatus.UPCOMING, pageable)
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
        return eventRepository.findByTag(tag, EventStatus.UPCOMING, pageable)
    }

    // ===========================================
    // PRICE TEMPLATE MANAGEMENT
    // ===========================================

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
     * @return Page of EventSession entities.
     */
    @Transactional(readOnly = true)
    fun getEventSessions(eventId: UUID, pageable: Pageable): Page<EventSession> {
        logger.debug { "Fetching sessions for event: $eventId" }
        return eventSessionRepository.findByEventId(eventId, pageable)
    }

    /**
     * Get upcoming bookable sessions for an event.
     *
     * @param eventId The ID of the event.
     * @return List of EventSession entities.
     */
    @Transactional(readOnly = true)
    fun getBookableSessions(eventId: UUID): List<EventSession> {
        logger.debug { "Fetching bookable sessions for event: $eventId" }
        return eventSessionRepository.findBookableSessions(eventId, Instant.now())
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
    }
}

