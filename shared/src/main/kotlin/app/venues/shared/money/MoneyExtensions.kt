package app.venues.shared.money

import java.math.BigDecimal

fun BigDecimal.toMoney(currency: String): MoneyAmount = MoneyAmount(this, currency)
