package app.venues.ticket.api.security

import app.venues.ticket.api.ScannerSessionApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class TicketSecurityConfig(
    private val sessionApi: ScannerSessionApi
) {

    @Bean
    @Order(2)
    fun ticketSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/api/v1/tickets/scan/**")
            .authorizeHttpRequests { auth ->
                auth.anyRequest().authenticated()
            }
            .csrf { it.disable() }
            .addFilterBefore(
                ScannerAuthenticationFilter(sessionApi),
                UsernamePasswordAuthenticationFilter::class.java
            )

        return http.build()
    }
}
