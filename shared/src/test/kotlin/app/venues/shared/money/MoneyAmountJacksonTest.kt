package app.venues.shared.money

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MoneyAmountJacksonTest {
    private val mapper = jacksonObjectMapper()

    @Test
    fun `serializes amount as quoted decimal string`() {
        val json = mapper.writeValueAsString(MoneyAmount(BigDecimal("1234.5000"), "USD"))

        assertThat(json).contains("\"amount\":\"1234.5000\"")
    }

    @Test
    fun `deserializes amount from string`() {
        val payload = """{"amount":"42.10","currency":"EUR"}"""

        val result: MoneyAmount = mapper.readValue(payload)

        assertThat(result.amount).isEqualByComparingTo(BigDecimal("42.10"))
        assertThat(result.currency).isEqualTo("EUR")
    }
}
