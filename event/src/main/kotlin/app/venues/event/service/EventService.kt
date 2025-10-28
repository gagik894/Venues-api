package app.venues.event.service

import app.venues.common.exception.VenuesException
import app.venues.event.api.dto.*
import app.venues.event.api.mapper.EventMapper
import app.venues.event.domain.Event
import app.venues.event.domain.EventSession
import app.venues.event.domain.EventStatus
import app.venues.event.domain.EventTranslation
import app.venues.event.repository.EventRepository
import app.venues.event.repository.EventSessionRepository
import app.venues.seating.repository.SeatingChartRepository
import app.venues.venue.repository.VenueRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Service for event management operations.
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
    private val seatingChartRepository: SeatingChartRepository,
    private val venueRepository: VenueRepository,
    private val eventMapper: EventMapper
) {
    private val logger = KotlinLogging.logger {}

    // ===========================================
    // EVENT CRUD
    // ===========================================

    /**
     * Create a new event for a venue.
     */
    fun createEvent(venueId: Long, request: EventRequest): EventResponse {
        logger.debug { "Creating event for venue: $venueId" }

        val venue = venueRepository.findById(venueId)
            .orElseThrow { VenuesException.ResourceNotFound("Venue not found with ID: $venueId") }

        // Fetch seating chart if provided
        val seatingChart = request.seatingChartId?.let { chartId ->
            seatingChartRepository.findById(chartId)
                .orElseThrow { VenuesException.ResourceNotFound("Seating chart not found with ID: $chartId") }
        }

        val event = Event(
            title = request.title,
            description = request.description,
            imgUrl = request.imgUrl,
            venue = venue,
            location = request.location,
            latitude = request.latitude,
            longitude = request.longitude,
            priceRange = request.priceRange,
            currency = request.currency,
            seatingChart = seatingChart,
            status = request.status
        )

        // Add secondary images
        event.secondaryImgUrls.addAll(request.secondaryImgUrls)

        // Add tags
        event.tags.addAll(request.tags)

        val savedEvent = eventRepository.save(event)
        logger.info { "Event created successfully: ID=${savedEvent.id}" }

        return eventMapper.toResponse(savedEvent)
    }

    /**
     * Get event by ID.
     */
    @Transactional(readOnly = true)
    fun getEventById(id: Long, includeStats: Boolean = false): EventResponse {
        logger.debug { "Fetching event by ID: $id" }

        val event = eventRepository.findById(id)
            .orElseThrow { VenuesException.ResourceNotFound("Event not found with ID: $id") }

        return eventMapper.toResponse(event, includeStats)
    }

    /**
     * Update event.
     */
    fun updateEvent(eventId: Long, venueId: Long, request: EventRequest): EventResponse {
        logger.debug { "Updating event: $eventId for venue: $venueId" }

        val event = eventRepository.findById(eventId)
            .orElseThrow { VenuesException.ResourceNotFound("Event not found with ID: $eventId") }

        // Verify ownership
        if (event.venue.id != venueId) {
            throw VenuesException.AuthorizationFailure("You can only update your own events")
        }

        // Check if event is editable
        if (!event.isEditable()) {
            throw VenuesException.ValidationFailure("Event cannot be edited in current status: ${event.status}")
        }

        // Fetch seating chart if provided
        val seatingChart = request.seatingChartId?.let { chartId ->
            seatingChartRepository.findById(chartId)
                .orElseThrow { VenuesException.ResourceNotFound("Seating chart not found with ID: $chartId") }
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
        event.seatingChart = seatingChart
        event.status = request.status

        // Update collections
        event.secondaryImgUrls.clear()
        event.secondaryImgUrls.addAll(request.secondaryImgUrls)
        event.tags.clear()
        event.tags.addAll(request.tags)

        val savedEvent = eventRepository.save(event)
        logger.info { "Event updated successfully: $eventId" }

        return eventMapper.toResponse(savedEvent)
    }

    /**
     * Delete event.
     */
    fun deleteEvent(eventId: Long, venueId: Long) {
        logger.debug { "Deleting event: $eventId" }

        val event = eventRepository.findById(eventId)
            .orElseThrow { VenuesException.ResourceNotFound("Event not found with ID: $eventId") }

        // Verify ownership
        if (event.venue.id != venueId) {
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
    fun getAllEvents(pageable: Pageable): Page<EventResponse> {
        logger.debug { "Fetching all publicly visible events" }
        return eventRepository.findByStatus(EventStatus.UPCOMING, pageable)
            .map { eventMapper.toResponse(it, includeStats = true) }
    }

    /**
     * Search events by title.
     */
    @Transactional(readOnly = true)
    fun searchEvents(searchTerm: String, pageable: Pageable): Page<EventResponse> {
        logger.debug { "Searching events: $searchTerm" }
        return eventRepository.searchByTitle(searchTerm, EventStatus.UPCOMING, pageable)
            .map { eventMapper.toResponse(it, includeStats = true) }
    }

    /**
     * Get events by venue.
     */
    @Transactional(readOnly = true)
    fun getEventsByVenue(venueId: Long, pageable: Pageable): Page<EventResponse> {
        logger.debug { "Fetching events for venue: $venueId" }
        return eventRepository.findByVenueIdAndStatus(venueId, EventStatus.UPCOMING, pageable)
            .map { eventMapper.toResponse(it, includeStats = true) }
    }

    /**
     * Get events by category.
     */
    @Transactional(readOnly = true)
    fun getEventsByCategory(categoryId: Long, pageable: Pageable): Page<EventResponse> {
        logger.debug { "Fetching events for category: $categoryId" }
        return eventRepository.findByCategoryIdAndStatus(categoryId, EventStatus.UPCOMING, pageable)
            .map { eventMapper.toResponse(it, includeStats = true) }
    }

    /**
     * Get events by tag.
     */
    @Transactional(readOnly = true)
    fun getEventsByTag(tag: String, pageable: Pageable): Page<EventResponse> {
        logger.debug { "Fetching events for tag: $tag" }
        return eventRepository.findByTag(tag, EventStatus.UPCOMING, pageable)
            .map { eventMapper.toResponse(it, includeStats = true) }
    }

    // ===========================================
    // EVENT SESSIONS
    // ===========================================

    /**
     * Add session to event.
     */
    fun addSession(eventId: Long, request: EventSessionRequest): EventSessionResponse {
        logger.debug { "Adding session to event: $eventId" }

        val event = eventRepository.findById(eventId)
            .orElseThrow { VenuesException.ResourceNotFound("Event not found with ID: $eventId") }

        // Validate session times
        if (request.startTime.isAfter(request.endTime)) {
            throw VenuesException.ValidationFailure("Start time must be before end time")
        }

        val session = EventSession(
            event = event,
            startTime = request.startTime,
            endTime = request.endTime,
            ticketsCount = request.ticketsCount,
            status = request.status,
            priceOverride = request.priceOverride,
            priceRangeOverride = request.priceRangeOverride
        )

        val savedSession = eventSessionRepository.save(session)
        logger.info { "Session added successfully: ID=${savedSession.id}" }

        return eventMapper.toSessionResponse(savedSession)
    }

    /**
     * Get sessions for an event.
     */
    @Transactional(readOnly = true)
    fun getEventSessions(eventId: Long, pageable: Pageable): Page<EventSessionResponse> {
        logger.debug { "Fetching sessions for event: $eventId" }
        return eventSessionRepository.findByEventId(eventId, pageable)
            .map { eventMapper.toSessionResponse(it) }
    }

    /**
     * Get upcoming bookable sessions for an event.
     */
    @Transactional(readOnly = true)
    fun getBookableSessions(eventId: Long): List<EventSessionResponse> {
        logger.debug { "Fetching bookable sessions for event: $eventId" }
        return eventSessionRepository.findBookableSessions(eventId, Instant.now())
            .map { eventMapper.toSessionResponse(it) }
    }

    /**
     * Update session.
     */
    fun updateSession(sessionId: Long, request: EventSessionRequest): EventSessionResponse {
        logger.debug { "Updating session: $sessionId" }

        val session = eventSessionRepository.findById(sessionId)
            .orElseThrow { VenuesException.ResourceNotFound("Session not found with ID: $sessionId") }

        // Validate session times
        if (request.startTime.isAfter(request.endTime)) {
            throw VenuesException.ValidationFailure("Start time must be before end time")
        }

        session.startTime = request.startTime
        session.endTime = request.endTime
        session.ticketsCount = request.ticketsCount
        session.status = request.status
        session.priceOverride = request.priceOverride
        session.priceRangeOverride = request.priceRangeOverride

        val savedSession = eventSessionRepository.save(session)
        logger.info { "Session updated successfully: $sessionId" }

        return eventMapper.toSessionResponse(savedSession)
    }

    /**
     * Delete session.
     */
    fun deleteSession(sessionId: Long) {
        logger.debug { "Deleting session: $sessionId" }

        val session = eventSessionRepository.findById(sessionId)
            .orElseThrow { VenuesException.ResourceNotFound("Session not found with ID: $sessionId") }

        eventSessionRepository.delete(session)
        logger.info { "Session deleted successfully: $sessionId" }
    }

    // ===========================================
    // TRANSLATIONS
    // ===========================================

    /**
     * Add or update translation for an event.
     */
    fun setTranslation(eventId: Long, request: EventTranslationRequest): EventTranslationResponse {
        logger.debug { "Setting translation for event: $eventId, language: ${request.language}" }

        val event = eventRepository.findById(eventId)
            .orElseThrow { VenuesException.ResourceNotFound("Event not found with ID: $eventId") }

        // Find existing translation or create new
        val translation = event.translations.find { it.language == request.language.lowercase() }
            ?: EventTranslation(
                event = event,
                language = request.language.lowercase(),
                title = request.title,
                description = request.description
            ).also { event.addTranslation(it) }

        // Update translation
        translation.title = request.title
        translation.description = request.description

        val savedEvent = eventRepository.save(event)
        val savedTranslation = savedEvent.translations.find { it.language == request.language.lowercase() }!!

        logger.info { "Translation set successfully for event: $eventId, language: ${request.language}" }

        return eventMapper.toTranslationResponse(savedTranslation)
    }

    /**
     * Get translations for an event.
     */
    @Transactional(readOnly = true)
    fun getTranslations(eventId: Long): List<EventTranslationResponse> {
        logger.debug { "Fetching translations for event: $eventId" }

        val event = eventRepository.findById(eventId)
            .orElseThrow { VenuesException.ResourceNotFound("Event not found with ID: $eventId") }

        return event.translations.map { eventMapper.toTranslationResponse(it) }
    }
}

