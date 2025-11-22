package app.venues.payment.controller

import app.venues.payment.api.PaymentApi
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/payments/callbacks")
class PaymentCallbackController(
    private val paymentApi: PaymentApi
) {

    /**
     * Handles POST callbacks (e.g. Telcell, Idram).
     * Accepts both query parameters and form/json body.
     */
    @PostMapping("/{providerId}")
    fun handlePostCallback(
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
    fun handleGetCallback(
        @PathVariable providerId: String,
        @RequestParam allParams: Map<String, String>
    ): ResponseEntity<Any> {
        val result = paymentApi.processCallback(providerId, allParams)
        return ResponseEntity.ok(result)
    }
}
