package app.venues.booking.api.controller

import app.venues.booking.api.dto.AddSeatToCartRequest
import app.venues.booking.api.dto.CartItemType
import app.venues.booking.api.dto.CartMutationResponse
import app.venues.booking.api.dto.CartSummaryResponse
import app.venues.booking.service.CartQueryService
import app.venues.booking.service.CartService
import app.venues.booking.service.StaffCartService
import app.venues.shared.money.MoneyAmount
import app.venues.venue.api.service.VenueSecurityService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.*

class StaffCartControllerTest {

    private lateinit var mockMvc: MockMvc
    private lateinit var cartService: CartService
    private lateinit var cartQueryService: CartQueryService
    private lateinit var staffCartService: StaffCartService
    private lateinit var venueSecurityService: VenueSecurityService
    private val objectMapper = ObjectMapper()

    private val token = UUID.randomUUID()
    private val sessionId = UUID.randomUUID()
    private val venueId = UUID.randomUUID()
    private val staffId = UUID.randomUUID()

    private val emptySummary = CartSummaryResponse(
        token = token,
        seats = emptyList(),
        gaItems = emptyList(),
        tables = emptyList(),
        totalPrice = MoneyAmount.zero("USD"),
        currency = "USD",
        expiresAt = "2023-01-01T12:00:00Z",
        sessionId = sessionId,
        eventTitle = "Test Event"
    )

    @BeforeEach
    fun setup() {
        cartService = mock(CartService::class.java)
        cartQueryService = mock(CartQueryService::class.java)
        staffCartService = mock(StaffCartService::class.java)
        venueSecurityService = mock(VenueSecurityService::class.java)

        val controller = StaffCartController(cartService, cartQueryService, staffCartService, venueSecurityService)

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @Test
    fun `addSeatToCart returns CartMutationResponse`() {
        val request = AddSeatToCartRequest(sessionId, "A1")
        val mutationResponse = CartMutationResponse(
            cartToken = token,
            success = true,
            affectedItemId = "A1",
            affectedItemType = CartItemType.SEAT
        )

        `when`(cartService.addSeatToCart(request, token, true, null)).thenReturn(mutationResponse)

        mockMvc.perform(
            post("/api/v1/staff/venues/$venueId/cart/seats")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .param("token", token.toString())
                .requestAttr("staffId", staffId)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.cartToken").value(token.toString()))
            .andExpect(jsonPath("$.data.success").value(true))
            .andExpect(jsonPath("$.data.affectedItemId").value("A1"))
            .andExpect(jsonPath("$.data.affectedItemType").value("SEAT"))

        verify(venueSecurityService).requireVenueSellPermission(staffId, venueId)
        verify(cartService).addSeatToCart(request, token, true, null)
    }

    @Test
    fun `removeSeatFromCart returns CartSummaryResponse`() {
        `when`(cartService.removeSeatFromCart(token, "A1", null)).thenReturn(emptySummary)

        mockMvc.perform(
            delete("/api/v1/staff/venues/$venueId/cart/seats/A1")
                .param("token", token.toString())
                .requestAttr("staffId", staffId)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.token").value(token.toString()))

        verify(venueSecurityService).requireVenueSellPermission(staffId, venueId)
        verify(cartService).removeSeatFromCart(token, "A1", null)
    }

    @Test
    fun `clearCart returns CartSummaryResponse`() {
        `when`(cartService.clearCart(token, null)).thenReturn(emptySummary)

        mockMvc.perform(
            delete("/api/v1/staff/venues/$venueId/cart/clear")
                .param("token", token.toString())
                .requestAttr("staffId", staffId)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.token").value(token.toString()))

        verify(venueSecurityService).requireVenueSellPermission(staffId, venueId)
        verify(cartService).clearCart(token, null)
    }
}
