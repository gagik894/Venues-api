package app.venues.platform.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for Platform entity domain logic.
 *
 * Tests verify:
 * - Active/inactive state management
 * - Webhook enable/disable functionality
 * - Webhook statistics tracking
 */
class PlatformTest {

    // ===========================================
    // PLATFORM STATUS TESTS
    // ===========================================

    @Test
    fun `new platform is active by default`() {
        val platform = createPlatform()

        assertTrue(platform.isActive())
        assertEquals(PlatformStatus.ACTIVE, platform.status)
    }

    @Test
    fun `deactivate sets status to INACTIVE`() {
        val platform = createPlatform()
        assertTrue(platform.isActive())

        platform.deactivate()

        assertFalse(platform.isActive())
        assertEquals(PlatformStatus.INACTIVE, platform.status)
    }

    @Test
    fun `deactivate is idempotent`() {
        val platform = createPlatform()

        platform.deactivate()
        platform.deactivate()
        platform.deactivate()

        assertFalse(platform.isActive())
        assertEquals(PlatformStatus.INACTIVE, platform.status)
    }

    // ===========================================
    // WEBHOOK ENABLE/DISABLE TESTS
    // ===========================================

    @Test
    fun `webhooks are enabled by default`() {
        val platform = createPlatform()
        assertTrue(platform.webhookEnabled)
    }

    @Test
    fun `disableWebhooks sets webhookEnabled to false`() {
        val platform = createPlatform()
        assertTrue(platform.webhookEnabled)

        platform.disableWebhooks()

        assertFalse(platform.webhookEnabled)
    }

    @Test
    fun `enableWebhooks sets webhookEnabled to true`() {
        val platform = createPlatform()
        platform.disableWebhooks()
        assertFalse(platform.webhookEnabled)

        platform.enableWebhooks()

        assertTrue(platform.webhookEnabled)
    }

    @Test
    fun `enable and disable webhooks are idempotent`() {
        val platform = createPlatform()

        platform.disableWebhooks()
        platform.disableWebhooks()
        assertFalse(platform.webhookEnabled)

        platform.enableWebhooks()
        platform.enableWebhooks()
        assertTrue(platform.webhookEnabled)
    }

    // ===========================================
    // SHOULD RECEIVE WEBHOOKS TESTS
    // ===========================================

    @Test
    fun `shouldReceiveWebhooks returns true when active and enabled`() {
        val platform = createPlatform()

        assertTrue(platform.isActive())
        assertTrue(platform.webhookEnabled)
        assertTrue(platform.shouldReceiveWebhooks())
    }

    @Test
    fun `shouldReceiveWebhooks returns false when inactive`() {
        val platform = createPlatform()
        platform.deactivate()

        assertFalse(platform.shouldReceiveWebhooks())
    }

    @Test
    fun `shouldReceiveWebhooks returns false when webhooks disabled`() {
        val platform = createPlatform()
        platform.disableWebhooks()

        assertFalse(platform.shouldReceiveWebhooks())
    }

    @Test
    fun `shouldReceiveWebhooks returns false when both inactive and disabled`() {
        val platform = createPlatform()
        platform.deactivate()
        platform.disableWebhooks()

        assertFalse(platform.shouldReceiveWebhooks())
    }

    // ===========================================
    // WEBHOOK STATISTICS TESTS
    // ===========================================

    @Test
    fun `initial webhook counts are zero`() {
        val platform = createPlatform()

        assertEquals(0L, platform.webhookSuccessCount)
        assertEquals(0L, platform.webhookFailureCount)
    }

    @Test
    fun `initial webhook timestamps are null`() {
        val platform = createPlatform()

        assertNull(platform.lastWebhookSuccess)
        assertNull(platform.lastWebhookFailure)
    }

    @Test
    fun `recordWebhookSuccess increments count and sets timestamp`() {
        val platform = createPlatform()

        platform.recordWebhookSuccess()

        assertEquals(1L, platform.webhookSuccessCount)
        assertNotNull(platform.lastWebhookSuccess)
    }

    @Test
    fun `recordWebhookSuccess can be called multiple times`() {
        val platform = createPlatform()

        platform.recordWebhookSuccess()
        platform.recordWebhookSuccess()
        platform.recordWebhookSuccess()

        assertEquals(3L, platform.webhookSuccessCount)
        assertNotNull(platform.lastWebhookSuccess)
    }

    @Test
    fun `recordWebhookFailure increments count and sets timestamp`() {
        val platform = createPlatform()

        platform.recordWebhookFailure()

        assertEquals(1L, platform.webhookFailureCount)
        assertNotNull(platform.lastWebhookFailure)
    }

    @Test
    fun `recordWebhookFailure can be called multiple times`() {
        val platform = createPlatform()

        platform.recordWebhookFailure()
        platform.recordWebhookFailure()
        platform.recordWebhookFailure()

        assertEquals(3L, platform.webhookFailureCount)
        assertNotNull(platform.lastWebhookFailure)
    }

    @Test
    fun `success and failure counts are independent`() {
        val platform = createPlatform()

        platform.recordWebhookSuccess()
        platform.recordWebhookSuccess()
        platform.recordWebhookFailure()

        assertEquals(2L, platform.webhookSuccessCount)
        assertEquals(1L, platform.webhookFailureCount)
    }

    @Test
    fun `recordWebhookSuccess updates timestamp each time`() {
        val platform = createPlatform()

        platform.recordWebhookSuccess()
        val firstTimestamp = platform.lastWebhookSuccess

        Thread.sleep(10) // Small delay to ensure different timestamp
        platform.recordWebhookSuccess()
        val secondTimestamp = platform.lastWebhookSuccess

        assertNotNull(firstTimestamp)
        assertNotNull(secondTimestamp)
        assertTrue(secondTimestamp!! >= firstTimestamp!!)
    }

    // ===========================================
    // HELPER METHODS
    // ===========================================

    private fun createPlatform(): Platform {
        return Platform(
            name = "TestPlatform",
            apiUrl = "https://api.test.com/webhook",
            sharedSecret = "test-secret-12345"
        )
    }
}
