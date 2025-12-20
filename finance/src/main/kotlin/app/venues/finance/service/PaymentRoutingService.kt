package app.venues.finance.service

import app.venues.finance.api.PaymentRoutingApi
import app.venues.finance.api.dto.MerchantProfileDto
import app.venues.finance.domain.MerchantProfile
import app.venues.finance.repository.MerchantProfileRepository
import app.venues.organization.api.OrganizationApi
import app.venues.venue.api.VenueApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Service responsible for resolving the correct MerchantProfile for a transaction.
 * Implements the "Financial Waterfall" algorithm.
 */

@Service
class PaymentRoutingService(
    private val venueApi: VenueApi,
    private val organizationApi: OrganizationApi,
    private val merchantProfileRepository: MerchantProfileRepository
) : PaymentRoutingApi {

    private val logger = KotlinLogging.logger {}

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
    override fun resolveMerchant(venueId: UUID, eventId: UUID?): MerchantProfileDto {
        logger.debug { "Resolving merchant for Venue: $venueId (Event: $eventId)" }

        // 1. [Future] Check Event specific profile
        // if (eventId != null) { ... }

        // 2. Check Venue override
        val venueInfo = venueApi.getVenueBasicInfo(venueId)
            ?: throw IllegalArgumentException("Venue not found: $venueId")

        venueInfo.merchantProfileId?.let { profileId ->
            val profile = merchantProfileRepository.findById(profileId).orElse(null)
                ?: throw IllegalStateException("Venue $venueId references missing MerchantProfile $profileId")
            logger.info { "Resolved Venue specific merchant profile: ${profile.name} ($profileId)" }
            return toDto(profile)
        }

        // 3. Fallback to Organization default
        val organizationId = venueInfo.organizationId

        val organizationDto = organizationApi.getOrganization(organizationId)
            ?: throw IllegalStateException("Organization not found: $organizationId")

        organizationDto.defaultMerchantProfileId?.let { profileId ->
            val profile = merchantProfileRepository.findById(profileId).orElse(null)
                ?: throw IllegalStateException("Organization $organizationId references missing default MerchantProfile $profileId")
            logger.info { "Resolved Organization default merchant profile: ${profile.name} ($profileId)" }
            return toDto(profile)
        }

        // 4. No profile found
        val errorMsg = "No MerchantProfile found for Venue $venueId (Org $organizationId). Configuration missing."
        logger.error { errorMsg }
        throw IllegalStateException(errorMsg)
    }

    @Transactional(readOnly = true)
    override fun getMerchant(merchantId: UUID): MerchantProfileDto {
        val profile = merchantProfileRepository.findById(merchantId)
            .orElseThrow { IllegalArgumentException("Merchant profile not found: $merchantId") }
        return toDto(profile)
    }

    private fun toDto(profile: MerchantProfile): MerchantProfileDto {
        return MerchantProfileDto(
            id = profile.id,
            name = profile.name,
            legalName = profile.legalName,
            taxId = profile.taxId,
            organizationId = profile.organizationId,
            config = profile.config // Pass the full config (including secrets) to the Payment Module
        )
    }
}
