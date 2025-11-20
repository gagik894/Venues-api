package app.venues.finance.repository

import app.venues.finance.domain.MerchantProfile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository for managing MerchantProfile entities.
 */
@Repository
interface MerchantProfileRepository : JpaRepository<MerchantProfile, UUID> {

    /**
     * Find all profiles belonging to an organization.
     */
    fun findByOrganizationId(organizationId: UUID): List<MerchantProfile>
}
