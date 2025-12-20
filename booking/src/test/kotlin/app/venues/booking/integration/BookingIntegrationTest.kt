package app.venues.booking.integration

import app.venues.audit.service.AuditActionRecorder
import app.venues.booking.api.BookingApi
import app.venues.booking.api.CartApi
import app.venues.shared.security.util.SecurityUtil
import app.venues.booking.domain.Cart
import app.venues.booking.repository.CartRepository
import app.venues.event.api.EventApi
import app.venues.seating.api.SeatingApi
import app.venues.ticket.api.TicketApi
import app.venues.user.api.UserApi
import app.venues.venue.api.VenueApi
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
@ActiveProfiles("test")
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@Import(app.venues.booking.TestApplication::class) // Retrieve entity scan config if needed, or just let DataJpaTest handle it
class BookingIntegrationTest {

    @Autowired
    private lateinit var cartRepository: CartRepository

    @Test
    fun `context loads and repository works`() {
        val sessionId = UUID.randomUUID()
        val cart = Cart(
            sessionId = sessionId,
            expiresAt = Instant.now().plusSeconds(600),
            token = UUID.randomUUID()
        )
        cartRepository.save(cart)
        
        val retrieved = cartRepository.findByToken(cart.token)
        assertNotNull(retrieved)
        assertEquals(sessionId, retrieved?.sessionId)
    }
}
