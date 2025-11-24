package app.venues.venue.api.controller

import app.venues.common.model.ApiResponse
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
@RequestMapping("/api/v1/staff/venues/{venueId}/promo-codes")
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
    ): ApiResponse<VenuePromoCodeResponse> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)
        val promoCode = promoCodeService.createPromoCode(venueId, request)
        return ApiResponse.success(promoCode, "Promotional code created successfully")
    }

    @GetMapping
    @Operation(
        summary = "List promo codes",
        description = "List all promotional codes for the venue. Supports optional fuzzy search."
    )
    fun listPromoCodes(
        @RequestAttribute staffId: UUID,
        @PathVariable venueId: UUID,
        @RequestParam(required = false) search: String?
    ): ApiResponse<List<VenuePromoCodeResponse>> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)

        val promoCodes = promoCodeService.getPromoCodes(venueId, search)

        return ApiResponse.success(promoCodes, "Promotional codes retrieved successfully")
    }

    @GetMapping("/{promoCodeId}")
    @Operation(
        summary = "Get promo code by ID",
        description = "Get details of a specific promotional code by its UUID."
    )
    fun getPromoCode(
        @RequestAttribute staffId: UUID,
        @PathVariable venueId: UUID,
        @PathVariable promoCodeId: UUID
    ): ApiResponse<VenuePromoCodeResponse> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)

        val promoCode = promoCodeService.getPromoCodeById(venueId, promoCodeId)

        return ApiResponse.success(promoCode, "Promotional code retrieved successfully")
    }

    @DeleteMapping("/{promoCodeId}")
    @Operation(
        summary = "Deactivate promo code",
        description = "Deactivate (soft delete) a promotional code."
    )
    fun deactivatePromoCode(
        @RequestAttribute staffId: UUID,
        @PathVariable venueId: UUID,
        @PathVariable promoCodeId: UUID
    ): ApiResponse<Unit> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)

        promoCodeService.deactivatePromoCode(venueId, promoCodeId)

        return ApiResponse.success(data = Unit, message = "Promotional code deactivated successfully")
    }

    @PutMapping("/{promoCodeId}")
    @Operation(
        summary = "Update promo code",
        description = "Update details of an existing promotional code (e.g., extend expiry, increase limit)."
    )
    fun updatePromoCode(
        @RequestAttribute staffId: UUID,
        @PathVariable venueId: UUID,
        @PathVariable promoCodeId: UUID,
        @Valid @RequestBody request: VenuePromoCodeRequest
    ): ApiResponse<VenuePromoCodeResponse> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)

        val updatedPromoCode = promoCodeService.updatePromoCode(venueId, promoCodeId, request)

        return ApiResponse.success(updatedPromoCode, "Promotional code updated successfully")
    }
}