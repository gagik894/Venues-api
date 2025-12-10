package app.venues.organization.service

import app.venues.organization.api.OrganizationApi
import app.venues.organization.api.dto.OrganizationDto
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
}
