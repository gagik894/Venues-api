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

        // Create sessions
        request.sessions.forEach { sessionRequest ->
            val session = createSessionEntity(event, sessionRequest)
            event.sessions.add(session)
        }

        // Create translations
        request.translations.forEach { translationRequest ->
            val translation = EventTranslation(
                event = event,
                language = translationRequest.language.lowercase(),
                title = translationRequest.title,
                description = translationRequest.description
            )
            event.addTranslation(translation)
        }

        val savedEvent = eventRepository.save(event)
        logger.info { "Event created successfully: ID=${savedEvent.id}" }

        val venueName = venueApi.getVenueName(venueId)
        val seatingChartName = savedEvent.seatingChartId?.let { seatingApi.getSeatingChartName(it) }

        return eventMapper.toResponse(savedEvent, venueName = venueName, seatingChartName = seatingChartName)
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

        // Update sessions
        updateSessionsCollection(event, request.sessions)

        // Update translations
        updateTranslationsCollection(event, request.translations)

        val savedEvent = eventRepository.save(event)
        logger.info { "Event updated successfully: $eventId" }

        val venueName = venueApi.getVenueName(venueId)
        val seatingChartName = savedEvent.seatingChartId?.let { seatingApi.getSeatingChartName(it) }

        return eventMapper.toResponse(savedEvent, venueName = venueName, seatingChartName = seatingChartName)
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

    /**
     * Map events page to response with batch-fetched venue names.
     *
     * This method implements the DRY principle by centralizing the logic for:
     * - Batch fetching venue names (single API call via VenueApi)
     * - Mapping events to responses with venue and seating chart names
     *
     * @param eventsPage Page of events to map
     * @param language Optional language for translations
     * @return Page of EventResponse with enriched venue information
     */
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

    /**
     * Map a single event to response with venue name and seating chart name.
     *
     * @param event Event entity to map
     * @param venueName Venue name (already fetched)
     * @param language Optional language for translations
     * @return EventResponse with enriched information
     */
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

    // ===========================================
    // EVENT SESSIONS
    // ===========================================

    /**
     * Add session to event.
     */
    fun addSession(eventId: UUID, venueId: UUID, request: EventSessionRequest): EventSessionResponse {
        logger.debug { "Adding session to event: $eventId for venue: $venueId" }

        val event = eventRepository.findById(eventId)
            .orElseThrow { VenuesException.ResourceNotFound("Event not found with ID: $eventId") }

        if (event.venueId != venueId) {
            throw VenuesException.AuthorizationFailure("You can only add sessions to your own events")
        }

        if (request.startTime.isAfter(request.endTime)) {
            throw VenuesException.ValidationFailure("Start time must be before end time")
        }

        val session = EventSession(
            event = event,
            startTime = request.startTime,
            endTime = request.endTime,
            ticketsCount = request.ticketsCount,
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
     * Update session.
     */
    fun updateSession(sessionId: UUID, venueId: UUID, request: EventSessionRequest): EventSessionResponse {
        logger.debug { "Updating session: $sessionId for venue: $venueId" }

        val session = eventSessionRepository.findById(sessionId)
            .orElseThrow { VenuesException.ResourceNotFound("Session not found with ID: $sessionId") }

        if (session.event.venueId != venueId) {
            throw VenuesException.AuthorizationFailure("You can only update sessions of your own events")
        }

        if (request.startTime.isAfter(request.endTime)) {
            throw VenuesException.ValidationFailure("Start time must be before end time")
        }

        session.startTime = request.startTime
        session.endTime = request.endTime
        session.ticketsCount = request.ticketsCount
        session.priceOverride = request.priceOverride
        session.priceRangeOverride = request.priceRangeOverride

        val savedSession = eventSessionRepository.save(session)
        logger.info { "Session updated successfully: $sessionId" }

        return eventMapper.toSessionResponse(savedSession)
    }

    /**
     * Delete session.
     */
    fun deleteSession(sessionId: UUID, venueId: UUID) {
        logger.debug { "Deleting session: $sessionId for venue: $venueId" }

        val session = eventSessionRepository.findById(sessionId)
            .orElseThrow { VenuesException.ResourceNotFound("Session not found with ID: $sessionId") }

        if (session.event.venueId != venueId) {
            throw VenuesException.AuthorizationFailure("You can only delete sessions of your own events")
        }

        eventSessionRepository.delete(session)
        logger.info { "Session deleted successfully: $sessionId" }
    }

    // ===========================================
    // TRANSLATIONS
    // ===========================================

    /**
     * Add or update translation for an event.
     */
    fun setTranslation(eventId: UUID, venueId: UUID, request: EventTranslationRequest): EventTranslationResponse {
        logger.debug { "Setting translation for event: $eventId, language: ${request.language} for venue: $venueId" }

        val event = eventRepository.findById(eventId)
            .orElseThrow { VenuesException.ResourceNotFound("Event not found with ID: $eventId") }

        if (event.venueId != venueId) {
            throw VenuesException.AuthorizationFailure("You can only translate your own events")
        }

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
    fun getTranslations(eventId: UUID): List<EventTranslationResponse> {
        logger.debug { "Fetching translations for event: $eventId" }

        val event = eventRepository.findById(eventId)
            .orElseThrow { VenuesException.ResourceNotFound("Event not found with ID: $eventId") }

        return event.translations.map { eventMapper.toTranslationResponse(it) }
    }

    private fun createSessionEntity(event: Event, request: EventSessionRequest): EventSession {
        if (request.startTime.isAfter(request.endTime)) {
            throw VenuesException.ValidationFailure("Start time must be before end time")
        }

        val session = EventSession(
            event = event,
            startTime = request.startTime,
            endTime = request.endTime,
            ticketsCount = request.ticketsCount,
            priceOverride = request.priceOverride,
            priceRangeOverride = request.priceRangeOverride
        )

        request.priceTemplateOverrides.forEach { overrideRequest ->
            val override = app.venues.event.domain.EventSessionPriceOverride(
                session = session,
                templateName = overrideRequest.templateName,
                price = overrideRequest.price
            )
            session.priceTemplateOverrides.add(override)
        }

        return session
    }

    private fun updateSessionsCollection(event: Event, sessionRequests: List<EventSessionRequest>) {
        // 1. Map existing sessions by ID
        val existingSessionsMap = event.sessions.associateBy { it.id }
        val requestSessionIds = sessionRequests.mapNotNull { it.id }.toSet()

        // 2. Identify sessions to remove
        val sessionsToRemove = event.sessions.filter { it.id !in requestSessionIds }
        sessionsToRemove.forEach { session ->
            if (session.ticketsSold > 0) {
                throw VenuesException.ValidationFailure("Cannot remove session with sold tickets: ${session.id}")
            }
        }
        event.sessions.removeAll(sessionsToRemove)

        // 3. Update or Create sessions
        sessionRequests.forEach { request ->
            if (request.id != null && existingSessionsMap.containsKey(request.id)) {
                // Update existing
                val session = existingSessionsMap[request.id]!!
                if (request.startTime.isAfter(request.endTime)) {
                    throw VenuesException.ValidationFailure("Start time must be before end time")
                }
                session.startTime = request.startTime
                session.endTime = request.endTime
                session.ticketsCount = request.ticketsCount
                session.priceOverride = request.priceOverride
                session.priceRangeOverride = request.priceRangeOverride

                // Update price overrides (replace all strategy for simplicity)
                session.priceTemplateOverrides.clear()
                request.priceTemplateOverrides.forEach { overrideRequest ->
                    val override = app.venues.event.domain.EventSessionPriceOverride(
                        session = session,
                        templateName = overrideRequest.templateName,
                        price = overrideRequest.price
                    )
                    session.priceTemplateOverrides.add(override)
                }
            } else {
                // Create new
                val session = createSessionEntity(event, request)
                event.sessions.add(session)
            }
        }
    }

    private fun updateTranslationsCollection(event: Event, translationRequests: List<EventTranslationRequest>) {
        // 1. Map existing translations by language
        val existingTranslationsMap = event.translations.associateBy { it.language }
        val requestLanguages = translationRequests.map { it.language.lowercase() }.toSet()

        // 2. Remove translations not in request
        event.translations.removeIf { it.language !in requestLanguages }

        // 3. Update or Create translations
        translationRequests.forEach { request ->
            val language = request.language.lowercase()
            if (existingTranslationsMap.containsKey(language)) {
                // Update existing
                val translation = existingTranslationsMap[language]!!
                translation.title = request.title
                translation.description = request.description
            } else {
                // Create new
                val translation = EventTranslation(
                    event = event,
                    language = language,
                    title = request.title,
                    description = request.description
                )
                event.addTranslation(translation)
            }
        }
    }
}

