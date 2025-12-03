package app.venues.event.support

import app.venues.common.exception.VenuesException
import app.venues.shared.money.MoneyAmount
import java.math.BigDecimal

fun MoneyAmount.requireCurrency(expected: String, label: String = "price"): BigDecimal {
    if (!currency.equals(expected, ignoreCase = true)) {
        throw VenuesException.ValidationFailure("$label currency must be $expected")
    }
    return amount
}
