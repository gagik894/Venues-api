package app.venues.booking.support

import app.venues.shared.money.MoneyAmount
import java.math.BigDecimal

fun BigDecimal.toMoney(currency: String): MoneyAmount = MoneyAmount(this, currency)
