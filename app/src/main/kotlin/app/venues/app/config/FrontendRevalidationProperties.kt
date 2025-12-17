package app.venues.app.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
@ConfigurationProperties(prefix = "frontend.revalidation")
class FrontendRevalidationProperties {
    var enabled: Boolean = true
    var secret: String? = null
    var scheme: String = "https"
    var timeout: Duration = Duration.ofSeconds(5)

    fun isConfigured(): Boolean = enabled && !secret.isNullOrBlank()
}
