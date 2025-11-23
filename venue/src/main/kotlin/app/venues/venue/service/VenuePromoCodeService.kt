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
import java.time.Instant
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
     * Normalizes code to UPPERCASE to ensure consistent lookup.
     */
    fun createPromoCode(venueId: UUID, request: VenuePromoCodeRequest): VenuePromoCodeResponse {
        val normalizedCode = request.code.trim().uppercase()

        logger.info { "Creating promo code for venue $venueId: $normalizedCode" }

        val venue = venueRepository.findById(venueId)
            .orElseThrow { VenuesException.ResourceNotFound("Venue not found") }

        if (promoCodeRepository.existsByVenueIdAndCode(venueId, normalizedCode)) {
            throw VenuesException.ResourceConflict("Promo code '$normalizedCode' already exists for this venue")
        }

        val promoCode = VenuePromoCode(
            venue = venue,
            code = normalizedCode,
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
     * Update an existing promo code.
     * Enforces strict uniqueness (name cannot exist, even if inactive).
     */
    fun updatePromoCode(venueId: UUID, promoCodeId: UUID, request: VenuePromoCodeRequest): VenuePromoCodeResponse {
        val normalizedCode = request.code.trim().uppercase()

        val promoCode = promoCodeRepository.findByIdAndVenueId(promoCodeId, venueId)
            .orElseThrow { VenuesException.ResourceNotFound("Promo code not found") }

        if (promoCode.code != normalizedCode) {
            if (promoCodeRepository.existsByVenueIdAndCode(venueId, normalizedCode)) {
                throw VenuesException.ResourceConflict("Promo code '$normalizedCode' already exists")
            }
        }

        if (request.maxUsageCount != null && request.maxUsageCount < promoCode.currentUsageCount) {
            throw VenuesException.ValidationFailure(
                "Cannot limit max usage to ${request.maxUsageCount} because ${promoCode.currentUsageCount} have already been used."
            )
        }

        promoCode.apply {
            this.code = normalizedCode
            this.discountType = request.discountType
            this.discountValue = request.discountValue
            this.description = request.description
            this.minOrderAmount = request.minOrderAmount
            this.maxDiscountAmount = request.maxDiscountAmount
            this.maxUsageCount = request.maxUsageCount
            this.expiresAt = request.expiresAt

        }

        val saved = promoCodeRepository.save(promoCode)
        return toResponse(saved)
    }

    /**
     * List all promo codes for a venue.
     */
    @Transactional(readOnly = true)
    fun getPromoCodes(venueId: UUID, search: String? = null): List<VenuePromoCodeResponse> {
        val codes = if (search.isNullOrBlank()) {
            promoCodeRepository.findByVenueId(venueId)
        } else {
            promoCodeRepository.findByVenueIdAndCodeContainingIgnoreCase(venueId, search)
        }
        return codes.map { toResponse(it) }
    }

    @Transactional(readOnly = true)
    fun getPromoCodeById(venueId: UUID, promoCodeId: UUID): VenuePromoCodeResponse {
        val promoCode = promoCodeRepository.findByIdAndVenueId(promoCodeId, venueId)
            .orElseThrow { VenuesException.ResourceNotFound("Promo code not found") }

        return toResponse(promoCode)
    }

    /**
     * Deactivate a promo code.
     */
    fun deactivatePromoCode(venueId: UUID, promoCodeId: UUID) {
        val promoCode = promoCodeRepository.findByIdAndVenueId(promoCodeId, venueId)
            .orElseThrow { VenuesException.ResourceNotFound("Promo code not found") }

        promoCode.deactivate()
        promoCodeRepository.save(promoCode)
        logger.info { "Deactivated promo code: $promoCodeId" }
    }

    /**
     * Validate a promo code for use (UI Feedback / Cart Preview).
     * Uses SQL logic to strictly check Active/Expired/Limit status.
     * Returns the entity if valid, throws generic exception if not.
     */
    @Transactional(readOnly = true)
    fun validatePromoCode(venueId: UUID, code: String): VenuePromoCode {
        val normalizedCode = code.trim().uppercase()

        val promoCode = promoCodeRepository.findValidPromoCodeByVenueIdAndCode(venueId, normalizedCode, Instant.now())
            .orElseThrow {
                VenuesException.ResourceNotFound("Promo code not found or invalid")
            }
        return promoCode
    }

    /**
     * Reserve a promo code (Order Creation).
     * Attempts to atomically increment the usage count.
     * If successful, the spot is held. If Order creation fails later, releasePromoCode MUST be called.
     */
    fun reservePromoCode(venueId: UUID, code: String) {
        val normalizedCode = code.trim().uppercase()

        val rowsUpdated = promoCodeRepository.incrementUsageIfAllowed(venueId, normalizedCode, Instant.now())

        if (rowsUpdated == 0) {
            throw VenuesException.ResourceNotFound("Promo code not found or invalid")
        }

        logger.info { "Reserved usage for code $normalizedCode" }
    }

    /**
     * Release a promo code (Cancellation / Payment Failure).
     * Decrements the usage count safely.
     */
    fun releasePromoCode(venueId: UUID, code: String) {
        val normalizedCode = code.trim().uppercase()

        promoCodeRepository.decrementUsage(venueId, normalizedCode)

        logger.info { "Released usage for code $normalizedCode" }
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