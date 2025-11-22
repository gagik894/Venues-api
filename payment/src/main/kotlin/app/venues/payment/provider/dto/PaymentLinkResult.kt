package app.venues.payment.provider.dto

data class PaymentLinkResult(
    val paymentUrl: String?,
    val formData: Map<String, String>? = null,
    val method: String = "GET", // GET (redirect) or POST (form submit)
    val gatewayReference: String? = null
)
