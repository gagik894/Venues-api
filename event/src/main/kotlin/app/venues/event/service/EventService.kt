package app.venues.event.service

import app.venues.common.exception.VenuesException
import app.venues.event.api.dto.*
import app.venues.event.api.mapper.EventMapper
import app.venues.event.domain.Event
import app.venues.event.domain.EventStatus
import app.venues.event.domain.EventTranslation
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
    private val eventSessionService: EventSessionService,
    private val eventPriceService: EventPriceService,
    private val eventMapper: EventMapper,
    private val venueApi: VenueApi,
    private val seatingApi: SeatingApi
) {
    private val logger = KotlinLogging.logger {}

    // ===========================================
    // EVENT CRUD
    // ===========================================

    /**
     * Create a new event for a venue.
     */
    fun createEvent(venueId: UUID, request: EventRequest): EventResponse {
        logger.debug { "Creating event for venue: $venueId" }

        if (!venueApi.venueExists(venueId)) {
            throw VenuesException.ResourceNotFound("Venue not found with ID: $venueId")
        }

        if (request.seatingChartId != null) {
            seatingApi.getSeatingChartName(request.seatingChartId)
                ?: throw VenuesException.ResourceNotFound("Seating chart not found with ID: ${request.seatingChartId}")
        }

        val event = Event(
            title = request.title,
            description = request.description,
            imgUrl = request.imgUrl,
            venueId = venueId,
            location = request.location,
            latitude = request.latitude,
            longitude = request.longitude,
            priceRange = request.priceRange,
            currency = request.currency,
            seatingChartId = request.seatingChartId,
        )

        event.secondaryImgUrls.addAll(request.secondaryImgUrls)
        event.tags.addAll(request.tags)

        // Delegate Price Templates
        eventPriceService.updatePriceTemplates(event, request.priceTemplates)

        // Delegate Sessions
        eventSessionService.updateSessions(event, request.sessions)

        // Create translations
        updateTranslationsCollection(event, request.translations)

        val savedEvent = eventRepository.save(event)

        // Generate configs for sessions now that event is saved
        eventSessionService.generateConfigsForNewSessions(savedEvent)
        
        logger.info { "Event created successfully: ID=${savedEvent.id}" }

        val venueName = venueApi.getVenueName(venueId)
        val seatingChartName = savedEvent.seatingChartId?.let { seatingApi.getSeatingChartName(it) }

        return eventMapper.toResponse(
            savedEvent,
            venueName = venueName,
            seatingChartName = seatingChartName
        )
    }

    /**
     * Get event by ID.
     */
    @Transactional(readOnly = true)
    fun getEventById(id: UUID, includeStats: Boolean = false, language: String? = null): EventResponse {
        logger.debug { "Fetching event by ID: $id, language: $language" }

        val event = eventRepository.findById(id)
            .orElseThrow { VenuesException.ResourceNotFound("Event not found with ID: $id") }

        val venueName = venueApi.getVenueNameTranslated(event.venueId, language) ?: "Unknown"
        val seatingChartName = event.seatingChartId?.let { seatingApi.getSeatingChartName(it) }

        return eventMapper.toResponse(
            event,
            venueName = venueName,
            seatingChartName = seatingChartName,
            includeStats = includeStats,
            language = language
        )
    }

    /**
     * Update event.
     */
    fun updateEvent(eventId: UUID, venueId: UUID, request: EventRequest): EventResponse {
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
        event.priceRange = request.priceRange
        event.currency = request.currency
        event.seatingChartId = request.seatingChartId

        // Update collections
        event.secondaryImgUrls.clear()
        event.secondaryImgUrls.addAll(request.secondaryImgUrls)
        event.tags.clear()
        event.tags.addAll(request.tags)

        // Delegate Price Templates
        eventPriceService.updatePriceTemplates(event, request.priceTemplates)

        // Delegate Sessions
        eventSessionService.updateSessions(event, request.sessions)

        // Update translations
        updateTranslationsCollection(event, request.translations)

        val savedEvent = eventRepository.save(event)

        // Generate configs for any new sessions
        eventSessionService.generateConfigsForNewSessions(savedEvent)
        
        logger.info { "Event updated successfully: $eventId" }

        val venueName = venueApi.getVenueName(venueId) ?: "Unknown"
        val seatingChartName = savedEvent.seatingChartId?.let { seatingApi.getSeatingChartName(it) }

        return eventMapper.toResponse(
            savedEvent,
            venueName = venueName,
            seatingChartName = seatingChartName
        )
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
     */
    @Transactional(readOnly = true)
    fun getAllEvents(pageable: Pageable, language: String? = null): Page<EventResponse> {
        logger.debug { "Fetching all publicly visible events, language: $language" }
        return mapEventsWithVenueNames(eventRepository.findByStatus(EventStatus.UPCOMING, pageable), language)
    }

    /**
     * Search events by title.
     */
    @Transactional(readOnly = true)
    fun searchEvents(searchTerm: String, pageable: Pageable, language: String? = null): Page<EventResponse> {
        logger.debug { "Searching events: $searchTerm, language: $language" }
        return mapEventsWithVenueNames(
            eventRepository.searchByTitle(searchTerm, EventStatus.UPCOMING, pageable),
            language
        )
    }

    /**
     * Get events by venue.
     */
    @Transactional(readOnly = true)
    fun getEventsByVenue(venueId: UUID, pageable: Pageable, language: String? = null): Page<EventResponse> {
        logger.debug { "Fetching events for venue: $venueId, language: $language" }

        val venueName = venueApi.getVenueNameTranslated(venueId, language) ?: "Unknown"

        return eventRepository.findByVenueIdAndStatus(venueId, EventStatus.UPCOMING, pageable)
            .map { event -> mapEventToResponse(event, venueName, language) }
    }

    /**
     * Get events by category.
     */
    @Transactional(readOnly = true)
    fun getEventsByCategory(categoryId: Long, pageable: Pageable, language: String? = null): Page<EventResponse> {
        logger.debug { "Fetching events for category: $categoryId, language: $language" }
        return mapEventsWithVenueNames(
            eventRepository.findByCategoryIdAndStatus(
                categoryId,
                EventStatus.UPCOMING,
                pageable
            ), language
        )
    }

    /**
     * Get events by tag.
     */
    @Transactional(readOnly = true)
    fun getEventsByTag(tag: String, pageable: Pageable, language: String? = null): Page<EventResponse> {
        logger.debug { "Fetching events for tag: $tag, language: $language" }
        return mapEventsWithVenueNames(eventRepository.findByTag(tag, EventStatus.UPCOMING, pageable), language)
    }

    // ===========================================
    // PRIVATE HELPER METHODS
    // ===========================================

    private fun mapEventsWithVenueNames(
        eventsPage: Page<Event>,
        language: String?
    ): Page<EventResponse> {
        val venueIds = eventsPage.content.map { it.venueId }.toSet()
        val venueNamesMap = venueApi.getVenueNamesBatch(venueIds, language)

        return eventsPage.map { event ->
            val venueName = venueNamesMap[event.venueId] ?: "Unknown"
            mapEventToResponse(event, venueName, language)
        }
    }

    private fun mapEventToResponse(
        event: Event,
        venueName: String,
        language: String?
    ): EventResponse {
        val seatingChartName = event.seatingChartId?.let {
            seatingApi.getSeatingChartName(it)
        }

        return eventMapper.toResponse(
            event,
            venueName = venueName,
            seatingChartName = seatingChartName,
            includeStats = true,
            language = language
        )
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
     */
    @Transactional(readOnly = true)
    fun getEventSessions(eventId: UUID, pageable: Pageable): Page<EventSessionResponse> {
        logger.debug { "Fetching sessions for event: $eventId" }
        return eventSessionRepository.findByEventId(eventId, pageable)
            .map { eventMapper.toSessionResponse(it) }
    }

    /**
     * Get upcoming bookable sessions for an event.
     */
    @Transactional(readOnly = true)
    fun getBookableSessions(eventId: UUID): List<EventSessionResponse> {
        logger.debug { "Fetching bookable sessions for event: $eventId" }
        return eventSessionRepository.findBookableSessions(eventId, Instant.now())
            .map { eventMapper.toSessionResponse(it) }
    }

    /**
     * Get translations for an event.
     */
    @Transactional(readOnly = true)
    fun getTranslations(eventId: UUID): List<EventTranslationResponse> {
        logger.debug { "Fetching translations for event: $eventId" }

        val event = eventRepository.findById(eventId)
            .orElseThrow { VenuesException.ResourceNotFound("Event not found with ID: $eventId") }

        return event.translations.map { eventMapper.toTranslationResponse(it) }
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

