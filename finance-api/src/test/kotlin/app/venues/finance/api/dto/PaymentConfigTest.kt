package app.venues.finance.api.dto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PaymentConfigTest {

    @Test
    fun `should mask secrets in toString`() {
        val idram = IdramConfig("123456", "super-secret")
        assertEquals("IdramConfig(recAccount='123456', secretKey='***')", idram.toString())

        val telcel = TelcelConfig("store-123", "issuer-1")
        assertEquals("TelcelConfig(storeKey='***', postponeBillIssuer=issuer-1)", telcel.toString())

        val arca = ArcaConfig("user", "pass")
        assertEquals("ArcaConfig(username='user', password='***')", arca.toString())

        val converse = ConverseConfig("merch-1", "sec-1")
        assertEquals("ConverseConfig(merchantId='merch-1', secretKey='***')", converse.toString())

        val stripe = StripeConfig("sk_live_123", "pk_live_123", "whsec_123")
        assertEquals("StripeConfig(publishableKey='pk_live_123', secretKey='***', webhookSecret='***')", stripe.toString())
    }

    @Test
    fun `should validate blank keys`() {
        assertThrows<IllegalArgumentException> {
            IdramConfig("")
        }
        assertThrows<IllegalArgumentException> {
            StripeConfig("", "pk_123")
        }
    }

    @Test
    fun `should detect active providers`() {
        val config = PaymentConfig(
            idram = IdramConfig("123", "sec")
        )
        assertTrue(config.hasAnyProvider())
        assertEquals(listOf("idram"), config.getConfiguredProviders())

        val emptyConfig = PaymentConfig()
        assertFalse(emptyConfig.hasAnyProvider())
        assertTrue(emptyConfig.getConfiguredProviders().isEmpty())
    }
}
