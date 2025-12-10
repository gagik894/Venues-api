package app.venues.organization.service

import app.venues.common.exception.VenuesException
import app.venues.common.model.ResponseMetadata
import app.venues.organization.api.OrganizationApi
import app.venues.organization.api.dto.CreateOrganizationRequest
import app.venues.organization.api.dto.OrganizationDetailResponse
import app.venues.organization.api.dto.OrganizationDto
import app.venues.organization.api.dto.UpdateOrganizationRequest
import app.venues.organization.domain.Organization
import app.venues.organization.repository.OrganizationRepository
import app.venues.shared.persistence.util.PageableMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*
import kotlin.jvm.optionals.getOrNull

@Service
class OrganizationService(
    private val organizationRepository: OrganizationRepository
) : OrganizationApi {

    @Transactional(readOnly = true)
    override fun getOrganization(id: UUID): OrganizationDto? {
        return organizationRepository.findById(id).getOrNull()?.let { org ->
            OrganizationDto(
                id = org.id,
                name = org.name,
                slug = org.slug,
                defaultMerchantProfileId = org.defaultMerchantProfileId
            )
        }
    }

    @Transactional(readOnly = true)
    override fun listOrganizations(limit: Int?, offset: Int?, includeInactive: Boolean): List<OrganizationDto> {
        val pageable = PageableMapper.createPageableUnsorted(limit, offset)

        val page = if (includeInactive) {
            organizationRepository.findAll(pageable)
        } else {
            organizationRepository.findAllActive(pageable)
        }

        return page
            .content
            .map { org ->
                OrganizationDto(
                    id = org.id,
                    name = org.name,
                    slug = org.slug,
                    defaultMerchantProfileId = org.defaultMerchantProfileId
                )
            }
    }

    /**
     * Get organization by custom domain (implements OrganizationApi interface).
     * Used for white-label site resolution.
     */
    @Transactional(readOnly = true)
    override fun getOrganizationByDomain(domain: String): OrganizationDto? {
        return organizationRepository.findByCustomDomain(domain)?.let { org ->
            OrganizationDto(
                id = org.id,
                name = org.name,
                slug = org.slug,
                defaultMerchantProfileId = org.defaultMerchantProfileId
            )
        }
    }

    @Transactional(readOnly = true)
    fun listOrganizationsWithMetadata(
        limit: Int?,
        offset: Int?,
        includeInactive: Boolean
    ): Pair<List<OrganizationDto>, ResponseMetadata> {
        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val page = if (includeInactive) {
            organizationRepository.findAll(pageable)
        } else {
            organizationRepository.findAllActive(pageable)
        }
        val data = page.content.map { org ->
            OrganizationDto(
                id = org.id,
                name = org.name,
                slug = org.slug,
                defaultMerchantProfileId = org.defaultMerchantProfileId
            )
        }
        val metadata = ResponseMetadata(
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages
        )
        return data to metadata
    }

    @Transactional(readOnly = true)
    fun getOrganizationDetail(id: UUID): OrganizationDetailResponse {
        val org = organizationRepository.findById(id).orElseThrow {
            VenuesException.ResourceNotFound("Organization not found", "ORG_NOT_FOUND")
        }
        return toDetail(org)
    }

    @Transactional
    fun createOrganization(request: CreateOrganizationRequest): OrganizationDetailResponse {
        if (organizationRepository.existsBySlug(request.slug)) {
            throw VenuesException.ResourceConflict("Organization slug already exists", "ORG_SLUG_EXISTS")
        }
        val org = Organization(
            name = request.name,
            slug = request.slug,
            type = request.type
        ).apply {
            legalName = request.legalName
            taxId = request.taxId
            contactEmail = request.email
            phoneNumber = request.phoneNumber
            websiteUrl = request.website
            // Unmodeled fields: description, registrationNumber, address, city, socialLinks, logo
        }
        val saved = organizationRepository.save(org)
        return toDetail(saved)
    }

    @Transactional
    fun updateOrganization(id: UUID, request: UpdateOrganizationRequest): OrganizationDetailResponse {
        val org = organizationRepository.findById(id).orElseThrow {
            VenuesException.ResourceNotFound("Organization not found", "ORG_NOT_FOUND")
        }
        request.name?.let { org.name = it }
        request.type?.let { org.type = it }
        request.legalName?.let { org.legalName = it }
        request.taxId?.let { org.taxId = it }
        request.email?.let { org.contactEmail = it }
        request.phoneNumber?.let { org.phoneNumber = it }
        request.website?.let { org.websiteUrl = it }
        request.isActive?.let { org.isActive = it }
        // Unmodeled fields ignored: description, registrationNumber, address, city, socialLinks, logo

        val saved = organizationRepository.save(org)
        return toDetail(saved)
    }

    private fun toDetail(org: Organization): OrganizationDetailResponse {
        return OrganizationDetailResponse(
            id = org.id!!,
            slug = org.slug,
            name = org.name,
            legalName = org.legalName,
            taxId = org.taxId,
            registrationNumber = null,
            description = null,
            type = org.type,
            address = null,
            citySlug = null,
            cityName = null,
            phoneNumber = org.phoneNumber,
            email = org.contactEmail,
            website = org.websiteUrl,
            socialLinks = null,
            logoUrl = null,
            isActive = org.isActive,
            staffCount = 0,
            venueCount = 0,
            createdAt = org.createdAt,
            lastModifiedAt = org.lastModifiedAt
        )
    }
}
