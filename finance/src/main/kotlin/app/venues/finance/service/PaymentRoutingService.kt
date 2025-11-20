package app.venues.finance.service

import app.venues.finance.domain.MerchantProfile
import app.venues.finance.repository.MerchantProfileRepository
import app.venues.organization.repository.OrganizationRepository
import app.venues.venue.repository.VenueRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*
import kotlin.jvm.optionals.getOrNull

/**
 * Service responsible for resolving the correct MerchantProfile for a transaction.
 * Implements the "Financial Waterfall" algorithm.
 */
@Service
class PaymentRoutingService(
    private val venueRepository: VenueRepository,
    private val organizationRepository: OrganizationRepository,
    private val merchantProfileRepository: MerchantProfileRepository
) {

    /**
     * Resolve the MerchantProfile to be used for a specific transaction context.
     *
     * Waterfall Logic:
     * 1. [Future] Event-specific override (e.g. "Co-hosted Event")
     * 2. Venue-specific override (e.g. "VIP Lounge" has its own merchant)
     * 3. Organization default (The standard merchant for the tenant)
     *
     * @param venueId The ID of the venue where the transaction is occurring.
     * @param eventId Optional ID of the event (for future specific overrides).
     * @return The resolved MerchantProfile.
     * @throws IllegalStateException if no valid merchant profile is found.
     */
    @Transactional(readOnly = true)
    fun resolveMerchant(venueId: UUID, eventId: UUID? = null): MerchantProfile {
        // 1. [Future] Check Event specific profile
        // if (eventId != null) { ... }

        // 2. Check Venue override
        val venue = venueRepository.findById(venueId).getOrNull()
            ?: throw IllegalArgumentException("Venue not found: $venueId")

        // Note: merchantProfileId field will be added to Venue entity
        venue.merchantProfileId?.let { profileId ->
            return merchantProfileRepository.findById(profileId).getOrNull()
                ?: throw IllegalStateException("Venue $venueId references missing MerchantProfile $profileId")
        }

        // 3. Fallback to Organization default
        val organizationId = venue.organizationId

        val organization = organizationRepository.findById(organizationId).getOrNull()
            ?: throw IllegalStateException("Organization not found: $organizationId")

        // Note: defaultMerchantProfileId field will be added to Organization entity
        organization.defaultMerchantProfileId?.let { profileId ->
            return merchantProfileRepository.findById(profileId).getOrNull()
                ?: throw IllegalStateException("Organization $organizationId references missing default MerchantProfile $profileId")
        }

        // 4. No profile found
        throw IllegalStateException("No MerchantProfile found for Venue $venueId (Org $organizationId). Configuration missing.")
    }
}
