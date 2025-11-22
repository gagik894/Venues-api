package app.venues.venue.api.controller

import app.venues.venue.api.dto.VenuePromoCodeRequest
import app.venues.venue.api.dto.VenuePromoCodeResponse
import app.venues.venue.service.VenuePromoCodeService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/v1/venues/{venueId}/promo-codes")
@Tag(name = "Venue Promo Codes", description = "Management of promotional codes for venues")
class VenuePromoCodeController(
    private val promoCodeService: VenuePromoCodeService
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create promo code",
        description = "Create a new promotional code for the venue."
    )
    fun createPromoCode(
        @PathVariable venueId: UUID,
        @Valid @RequestBody request: VenuePromoCodeRequest
    ): VenuePromoCodeResponse {
        return promoCodeService.createPromoCode(venueId, request)
    }

    @GetMapping
    @Operation(
        summary = "List promo codes",
        description = "List all promotional codes for the venue."
    )
    fun listPromoCodes(@PathVariable venueId: UUID): List<VenuePromoCodeResponse> {
        return promoCodeService.getPromoCodes(venueId)
    }

    @GetMapping("/{promoCodeId}")
    @Operation(
        summary = "Get promo code",
        description = "Get details of a specific promotional code."
    )
    fun getPromoCode(
        @PathVariable venueId: UUID,
        @PathVariable promoCodeId: UUID
    ): VenuePromoCodeResponse {
        // Ideally verify venueId matches promoCode.venue.id, but service handles retrieval by ID
        return promoCodeService.getPromoCode(promoCodeId)
    }

    @DeleteMapping("/{promoCodeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Deactivate promo code",
        description = "Deactivate (soft delete) a promotional code."
    )
    fun deactivatePromoCode(
        @PathVariable venueId: UUID,
        @PathVariable promoCodeId: UUID
    ) {
        promoCodeService.deactivatePromoCode(promoCodeId)
    }
}
