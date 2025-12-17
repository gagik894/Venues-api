package app.venues.payment.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "app.payment")
class PaymentProperties {
    /**
     * The base URL of the application (e.g. https://api.example.com).
     * Used for server-to-server callbacks.
     */
    var baseUrl: String = "http://localhost:8080"

    /**
     * The base URL of the frontend (e.g. https://traveler.am).
     * Used for user redirects.
     */
    var frontendUrl: String = "http://localhost:3000"
}
