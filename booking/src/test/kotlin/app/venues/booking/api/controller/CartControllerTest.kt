package app.venues.booking.api.controller

import app.venues.booking.api.dto.AddSeatToCartRequest
import app.venues.booking.api.dto.CartSummaryResponse
import app.venues.booking.api.dto.MoneyAmount
import app.venues.booking.service.CartQueryService
import app.venues.booking.service.CartService
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

class CartControllerTest {

    private lateinit var mockMvc: MockMvc
    private lateinit var cartService: CartService
    private lateinit var cartQueryService: CartQueryService
    private val objectMapper = ObjectMapper()

    private val token = UUID.randomUUID()
    private val sessionId = UUID.randomUUID()

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

        val controller = CartController(cartService, cartQueryService)

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @Test
    fun `addSeatToCart returns CartSummaryResponse`() {
        val request = AddSeatToCartRequest(sessionId, "A1")

        `when`(cartService.addSeatToCart(request, token)).thenReturn(emptySummary)

        mockMvc.perform(
            post("/api/v1/cart/seats")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .param("token", token.toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.token").value(token.toString()))
            .andExpect(jsonPath("$.data.totalPrice.amount").value(0))
            .andExpect(jsonPath("$.data.totalPrice.currency").value("USD"))

        verify(cartService).addSeatToCart(request, token)
    }

    @Test
    fun `removeSeatFromCart returns CartSummaryResponse`() {
        `when`(cartService.removeSeatFromCart(token, "A1")).thenReturn(emptySummary)

        mockMvc.perform(
            delete("/api/v1/cart/seats/A1")
                .param("token", token.toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.token").value(token.toString()))

        verify(cartService).removeSeatFromCart(token, "A1")
    }

    @Test
    fun `clearCart returns CartSummaryResponse`() {
        `when`(cartService.clearCart(token)).thenReturn(emptySummary)

        mockMvc.perform(
            delete("/api/v1/cart/clear")
                .param("token", token.toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.token").value(token.toString()))

        verify(cartService).clearCart(token)
    }
}
