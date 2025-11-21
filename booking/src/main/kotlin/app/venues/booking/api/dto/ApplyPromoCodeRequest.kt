package app.venues.booking.api.dto

import jakarta.validation.constraints.NotBlank

data class ApplyPromoCodeRequest(
    @field:NotBlank(message = "Promo code is required")
    val code: String
)
