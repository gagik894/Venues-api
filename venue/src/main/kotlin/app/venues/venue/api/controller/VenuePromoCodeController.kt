package app.venues.venue.api.controller

import app.venues.venue.api.dto.VenuePromoCodeRequest
import app.venues.venue.api.dto.VenuePromoCodeResponse
import app.venues.venue.service.VenuePromoCodeService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/v1/venues/{venueId}/promo-codes")
class VenuePromoCodeController(
    private val promoCodeService: VenuePromoCodeService
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createPromoCode(
        @PathVariable venueId: UUID,
        @Valid @RequestBody request: VenuePromoCodeRequest
    ): VenuePromoCodeResponse {
        return promoCodeService.createPromoCode(venueId, request)
    }

    @GetMapping
    fun listPromoCodes(@PathVariable venueId: UUID): List<VenuePromoCodeResponse> {
        return promoCodeService.getPromoCodes(venueId)
    }

    @GetMapping("/{promoCodeId}")
    fun getPromoCode(
        @PathVariable venueId: UUID,
        @PathVariable promoCodeId: UUID
    ): VenuePromoCodeResponse {
        // Ideally verify venueId matches promoCode.venue.id, but service handles retrieval by ID
        return promoCodeService.getPromoCode(promoCodeId)
    }

    @DeleteMapping("/{promoCodeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deactivatePromoCode(
        @PathVariable venueId: UUID,
        @PathVariable promoCodeId: UUID
    ) {
        promoCodeService.deactivatePromoCode(promoCodeId)
    }
}
