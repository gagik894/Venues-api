package app.venues.event.service

import app.venues.common.exception.VenuesException
import app.venues.event.api.dto.PriceTemplateRequest
import app.venues.event.domain.Event
import app.venues.event.domain.EventPriceTemplate
import app.venues.event.repository.EventRepository
import app.venues.event.support.requireCurrency
import app.venues.seating.api.SeatingApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Service for managing Event Price Templates.
 *
 * Responsibilities:
 * - Creating, Updating, Deleting price templates.
 * - Ensuring templates are linked to the correct event.
 */
@Service
@Transactional
class EventPriceService(
    private val eventRepository: EventRepository,
    private val seatingApi: SeatingApi
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Create or Update a collection of price templates for an event.
     * Handles the logic of matching existing templates by ID and creating new ones.
     */
    fun updatePriceTemplates(event: Event, requests: List<PriceTemplateRequest>) {
        val existingTemplatesMap = event.priceTemplates.associateBy { it.id }
        val requestTemplateIds = requests.mapNotNull { it.id }.toSet()

        // 1. Remove deleted templates
        val templatesToRemove = event.priceTemplates.filter { it.id !in requestTemplateIds }

        templatesToRemove.forEach { template ->
            if (template.isAnchor) {
                throw VenuesException.ValidationFailure(
                    "Cannot delete price template '${template.templateName}' because it matches a seating chart category (Anchor Template)."
                )
            }
        }

        event.priceTemplates.removeAll(templatesToRemove.toSet())

        // 2. Update or Create
        requests.forEach { request ->
            if (request.id != null && existingTemplatesMap.containsKey(request.id)) {
                // Update existing
                val template = existingTemplatesMap[request.id]!!
                template.templateName = request.templateName
                template.price =
                    request.price.requireCurrency(event.currency, "Price template '${request.templateName}'")
                template.color = request.color
            } else {
                // Create new
                val template = EventPriceTemplate(
                    event = event,
                    templateName = request.templateName,
                    price = request.price.requireCurrency(event.currency, "Price template '${request.templateName}'"),
                    color = request.color
                )
                event.priceTemplates.add(template)
            }
        }
    }

    /**
     * Create a single price template.
     */
    fun createTemplate(event: Event, request: PriceTemplateRequest): EventPriceTemplate {
        val template = EventPriceTemplate(
            event = event,
            templateName = request.templateName,
            price = request.price.requireCurrency(event.currency, "Price template '${request.templateName}'"),
            color = request.color
        )
        event.priceTemplates.add(template)
        return template
    }

    /**
     * Update a single price template.
     */
    fun updateTemplate(event: Event, templateId: UUID, request: PriceTemplateRequest): EventPriceTemplate {
        val template = event.priceTemplates.find { it.id == templateId }
            ?: throw VenuesException.ResourceNotFound("Price template not found: $templateId")

        template.templateName = request.templateName
        template.price = request.price.requireCurrency(event.currency, "Price template '${request.templateName}'")
        template.color = request.color

        return template
    }

    /**
     * Delete a single price template.
     */
    fun deleteTemplate(event: Event, templateId: UUID) {
        val template = event.priceTemplates.find { it.id == templateId }
            ?: throw VenuesException.ResourceNotFound("Price template not found: $templateId")

        if (template.isAnchor) {
            throw VenuesException.ValidationFailure(
                "Cannot delete price template '${template.templateName}' because it matches a seating chart category (Anchor Template)."
            )
        }

        event.priceTemplates.remove(template)
    }

    /**
     * Get a specific price template by ID.
     */
    fun getTemplate(templateId: UUID): EventPriceTemplate {
        // We might need a repository for templates if we want direct access, 
        // but usually we access via Event. For now, this is a placeholder if needed.
        // Since templates are part of the Event aggregate, we usually traverse from Event.
        throw UnsupportedOperationException("Direct template access not yet implemented. Access via Event.")
    }
}
