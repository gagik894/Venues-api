package app.venues.organization.service

import app.venues.common.exception.VenuesException
import app.venues.organization.api.OrganizationApi
import app.venues.organization.api.dto.*
import app.venues.organization.domain.Organization
import app.venues.organization.repository.OrganizationRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
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
    fun listOrganizations(pageable: Pageable, includeInactive: Boolean): Page<OrganizationResponse> {
        val page = if (includeInactive) {
            organizationRepository.findAll(pageable)
        } else {
            organizationRepository.findAllActive(pageable)
        }

        val data = page.map { org ->
            OrganizationResponse(
                id = org.id,
                name = org.name,
                slug = org.slug,
                type = org.type,
                isActive = org.isActive
            )
        }
        return data
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
            customDomain = request.customDomain
            defaultMerchantProfileId = request.defaultMerchantProfileId
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
        request.customDomain?.let { org.customDomain = it }
        request.defaultMerchantProfileId?.let { org.defaultMerchantProfileId = it }
        request.isActive?.let { org.isActive = it }

        val saved = organizationRepository.save(org)
        return toDetail(saved)
    }

    private fun toDetail(org: Organization): OrganizationDetailResponse {
        return OrganizationDetailResponse(
            id = org.id,
            slug = org.slug,
            name = org.name,
            legalName = org.legalName,
            taxId = org.taxId,
            type = org.type,
            phoneNumber = org.phoneNumber,
            email = org.contactEmail,
            website = org.websiteUrl,
            customDomain = org.customDomain,
            defaultMerchantProfileId = org.defaultMerchantProfileId,
            isActive = org.isActive,
            createdAt = org.createdAt,
            lastModifiedAt = org.lastModifiedAt
        )
    }
}
