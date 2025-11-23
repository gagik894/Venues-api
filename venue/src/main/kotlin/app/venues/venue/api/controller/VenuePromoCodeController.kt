package app.venues.venue.api.controller

import app.venues.venue.api.dto.VenuePromoCodeRequest
import app.venues.venue.api.dto.VenuePromoCodeResponse
import app.venues.venue.api.service.VenueSecurityService
import app.venues.venue.service.VenuePromoCodeService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('STAFF')")
@RequestMapping("/api/v1/venues/{venueId}/promo-codes")
@Tag(name = "Venue Promo Codes", description = "Management of promotional codes for venues")
class VenuePromoCodeController(
    private val promoCodeService: VenuePromoCodeService,
    private val venueSecurityService: VenueSecurityService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create promo code",
        description = "Create a new promotional code for the venue."
    )
    fun createPromoCode(
        @RequestAttribute staffId: UUID,
        @PathVariable venueId: UUID,
        @Valid @RequestBody request: VenuePromoCodeRequest
    ): VenuePromoCodeResponse {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)
        return promoCodeService.createPromoCode(venueId, request)
    }

    @GetMapping
    @Operation(
        summary = "List promo codes",
        description = "List all promotional codes for the venue."
    )
    fun listPromoCodes(
        @RequestAttribute staffId: UUID,
        @PathVariable venueId: UUID
    ): List<VenuePromoCodeResponse> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)
        return promoCodeService.getPromoCodes(venueId)
    }

    @GetMapping("/{code}")
    @Operation(
        summary = "Get promo code by code",
        description = "Get details of a specific promotional code."
    )
    fun getPromoCode(
        @RequestAttribute staffId: UUID,
        @PathVariable venueId: UUID,
        @PathVariable code: String
    ): VenuePromoCodeResponse {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)
        return promoCodeService.getPromoCodeByCode(venueId, code)
    }

    @DeleteMapping("/{promoCodeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Deactivate promo code",
        description = "Deactivate (soft delete) a promotional code."
    )
    fun deactivatePromoCode(
        @RequestAttribute staffId: UUID,
        @PathVariable venueId: UUID,
        @PathVariable promoCodeId: String
    ) {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)
        promoCodeService.deactivatePromoCode(venueId, promoCodeId)
    }
}
