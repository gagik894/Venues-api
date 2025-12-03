package app.venues.shared.money

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import java.math.BigDecimal

/**
 * Immutable, JSON-safe money representation shared across modules.
 */
data class MoneyAmount(
    @field:JsonSerialize(using = MoneyAmountSerializer::class)
    val amount: BigDecimal,
    val currency: String
) {
    init {
        require(currency.isNotBlank()) { "Currency must not be blank" }
        require(amount >= BigDecimal.ZERO) { "Amount must be non-negative" }
    }

    companion object {
        private val ZERO_AMOUNT = BigDecimal("0.00")

        fun zero(currency: String): MoneyAmount = MoneyAmount(ZERO_AMOUNT, currency)
    }
}

/**
 * Custom serializer that always emits the amount as a quoted decimal string,
 * ensuring no precision is lost in JSON consumers.
 */
class MoneyAmountSerializer : JsonSerializer<BigDecimal>() {
    override fun serialize(value: BigDecimal, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.toPlainString())
    }
}
