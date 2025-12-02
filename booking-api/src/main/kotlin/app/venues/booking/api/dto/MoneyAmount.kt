package app.venues.booking.api.dto

import java.math.BigDecimal

/**
 * Immutable money representation for API payloads.
 */
data class MoneyAmount(
    val amount: BigDecimal,
    val currency: String
) {
    init {
        require(currency.isNotBlank()) { "Currency must not be blank" }
    }

    companion object {
        fun zero(currency: String): MoneyAmount = MoneyAmount(BigDecimal.ZERO, currency)
    }
}
