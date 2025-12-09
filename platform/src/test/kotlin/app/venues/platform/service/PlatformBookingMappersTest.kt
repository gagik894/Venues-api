package app.venues.platform.service

import app.venues.platform.api.dto.PlatformEasyItemRequest
import app.venues.platform.api.dto.PlatformEasyReserveRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*

class PlatformBookingMappersTest {

    @Test
    fun `maps easy reserve request to direct sale request`() {
        val sessionId = UUID.randomUUID()
        val request = PlatformEasyReserveRequest(
            sessionId = sessionId,
            customerEmail = "customer@example.com",
            customerName = "Jane Customer",
            customerPhone = "123456789",
            items = listOf(
                PlatformEasyItemRequest(seatCode = "S1"),
                PlatformEasyItemRequest(gaAreaCode = "GA1", quantity = 2),
                PlatformEasyItemRequest(tableCode = "T1")
            ),
            promoCode = "PROMO10"
        )

        val mapped = request.toDirectSaleRequest()

        assertEquals(sessionId, mapped.sessionId)
        assertEquals(request.customerEmail, mapped.customerEmail)
        assertEquals(request.customerName, mapped.customerName)
        assertEquals(request.customerPhone, mapped.customerPhone)
        assertEquals(request.promoCode, mapped.promoCode)
        assertEquals(3, mapped.items.size)
        assertEquals("S1", mapped.items[0].seatCode)
        assertEquals("GA1", mapped.items[1].gaAreaCode)
        assertEquals(2, mapped.items[1].quantity)
        assertEquals("T1", mapped.items[2].tableCode)
    }
}

