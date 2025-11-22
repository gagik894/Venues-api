package app.venues.payment.controller

import app.venues.payment.api.PaymentApi
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/payments/callbacks")
@Tag(name = "Payment Callbacks", description = "Endpoints for payment provider callbacks (webhooks)")
class PaymentCallbackController(
    private val paymentApi: PaymentApi
) {

    /**
     * Handles POST callbacks (e.g. Telcell, Idram).
     * Accepts both query parameters and form/json body.
     */
    @PostMapping("/{providerId}")
    @Operation(
        summary = "Handle POST callback",
        description = "Process asynchronous payment notifications (webhooks) from providers via POST."
    )
    fun handlePostCallback(
        @Parameter(description = "Payment provider ID (e.g., telcell, idram)")
        @PathVariable providerId: String,
        @RequestParam allParams: Map<String, String>
    ): ResponseEntity<Any> {
        // Spring maps form-data to RequestParam map for POST requests as well
        val result = paymentApi.processCallback(providerId, allParams)
        return ResponseEntity.ok(result)
    }

    /**
     * Handles GET callbacks (e.g. Redirects, Arca).
     */
    @GetMapping("/{providerId}")
    @Operation(
        summary = "Handle GET callback",
        description = "Process synchronous payment redirects or notifications from providers via GET."
    )
    fun handleGetCallback(
        @Parameter(description = "Payment provider ID (e.g., arca, amerian)")
        @PathVariable providerId: String,
        @RequestParam allParams: Map<String, String>
    ): ResponseEntity<Any> {
        val result = paymentApi.processCallback(providerId, allParams)
        return ResponseEntity.ok(result)
    }
}
