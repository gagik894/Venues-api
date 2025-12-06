package app.venues.venue.api.dto

import app.venues.venue.dto.SmtpConfig

/**
 * Response DTO for SMTP config with masked password.
 */
data class SmtpConfigResponse(
    val email: String,
    val password: String,  // Masked
    val host: String,
    val port: Int,
    val tls: Boolean
) {
    companion object {
        fun from(config: SmtpConfig?): SmtpConfigResponse? {
            return config?.let {
                SmtpConfigResponse(
                    email = it.email,
                    password = it.password,
                    host = it.host,
                    port = it.port,
                    tls = it.tls
                )
            }
        }
    }
}
