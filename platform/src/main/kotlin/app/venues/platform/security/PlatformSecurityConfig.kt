package app.venues.platform.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * Security configuration for platform API endpoints.
 *
 * Configures HMAC-based authentication for platform API requests.
 */
@Configuration
@EnableWebSecurity
class PlatformSecurityConfig(
    private val platformAuthenticationFilter: PlatformAuthenticationFilter
) {

    /**
     * Security filter chain for platform API endpoints.
     * This runs before the main security chain.
     */
    @Bean
    @Order(1)
    fun platformSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/api/v1/platforms/**")
            .authorizeHttpRequests { auth ->
                auth.anyRequest().permitAll() // Platform auth handled by custom filter
            }
            .csrf { it.disable() } // CSRF not needed for API authentication
            .addFilterBefore(platformAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
}

