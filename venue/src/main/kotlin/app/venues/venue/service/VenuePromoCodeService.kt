package app.venues.venue.service

import app.venues.common.exception.VenuesException
import app.venues.venue.api.dto.VenuePromoCodeRequest
import app.venues.venue.api.dto.VenuePromoCodeResponse
import app.venues.venue.domain.VenuePromoCode
import app.venues.venue.repository.VenuePromoCodeRepository
import app.venues.venue.repository.VenueRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Transactional
class VenuePromoCodeService(
    private val promoCodeRepository: VenuePromoCodeRepository,
    private val venueRepository: VenueRepository
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Create a new promo code for a venue.
     */
    fun createPromoCode(venueId: UUID, request: VenuePromoCodeRequest): VenuePromoCodeResponse {
        logger.info { "Creating promo code for venue $venueId: ${request.code}" }

        val venue = venueRepository.findById(venueId)
            .orElseThrow { VenuesException.ResourceNotFound("Venue not found") }

        if (promoCodeRepository.existsByVenueIdAndCode(venueId, request.code)) {
            throw VenuesException.ResourceConflict("Promo code '${request.code}' already exists for this venue")
        }

        val promoCode = VenuePromoCode(
            venue = venue,
            code = request.code,
            discountType = request.discountType,
            discountValue = request.discountValue,
            description = request.description,
            minOrderAmount = request.minOrderAmount,
            maxDiscountAmount = request.maxDiscountAmount,
            maxUsageCount = request.maxUsageCount,
            expiresAt = request.expiresAt
        )

        val saved = promoCodeRepository.save(promoCode)
        return toResponse(saved)
    }

    /**
     * List all promo codes for a venue.
     */
    @Transactional(readOnly = true)
    fun getPromoCodes(venueId: UUID): List<VenuePromoCodeResponse> {
        return promoCodeRepository.findByVenueId(venueId)
            .map { toResponse(it) }
    }

    /**
     * Get a specific promo code.
     */
    @Transactional(readOnly = true)
    fun getPromoCode(id: UUID): VenuePromoCodeResponse {
        val promoCode = promoCodeRepository.findById(id)
            .orElseThrow { VenuesException.ResourceNotFound("Promo code not found") }
        return toResponse(promoCode)
    }

    /**
     * Deactivate a promo code.
     */
    fun deactivatePromoCode(id: UUID) {
        val promoCode = promoCodeRepository.findById(id)
            .orElseThrow { VenuesException.ResourceNotFound("Promo code not found") }
        promoCode.deactivate()
        promoCodeRepository.save(promoCode)
        logger.info { "Deactivated promo code: $id" }
    }

    /**
     * Validate a promo code for use.
     * Returns the entity if valid, throws exception if not.
     */
    @Transactional(readOnly = true)
    fun validatePromoCode(venueId: UUID, code: String): VenuePromoCode {
        val promoCode = promoCodeRepository.findByVenueIdAndCode(venueId, code)
            .orElseThrow { VenuesException.ResourceNotFound("Invalid promo code") }

        if (!promoCode.isValidForUse()) {
            throw VenuesException.ValidationFailure("Promo code is not valid for use (expired or limit reached)")
        }

        return promoCode
    }

    /**
     * Redeem a promo code (increment usage).
     */
    fun redeemPromoCode(venueId: UUID, code: String) {
        val promoCode = promoCodeRepository.findByVenueIdAndCode(venueId, code)
            .orElseThrow { VenuesException.ResourceNotFound("Invalid promo code") }

        if (!promoCode.redeem()) {
            throw VenuesException.ValidationFailure("Promo code cannot be redeemed (limit reached or expired)")
        }
        promoCodeRepository.save(promoCode)
    }

    private fun toResponse(entity: VenuePromoCode): VenuePromoCodeResponse {
        return VenuePromoCodeResponse(
            id = entity.id,
            code = entity.code,
            description = entity.description,
            discountType = entity.discountType,
            discountValue = entity.discountValue.toString(),
            minOrderAmount = entity.minOrderAmount?.toString(),
            maxDiscountAmount = entity.maxDiscountAmount?.toString(),
            maxUsageCount = entity.maxUsageCount,
            currentUsageCount = entity.currentUsageCount,
            expiresAt = entity.expiresAt,
            isActive = entity.isActive,
            createdAt = entity.createdAt
        )
    }
}
