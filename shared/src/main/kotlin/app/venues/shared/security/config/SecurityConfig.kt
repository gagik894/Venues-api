package app.venues.shared.security.config

import app.venues.shared.security.jwt.JwtAccessDeniedHandler
import app.venues.shared.security.jwt.JwtAuthenticationEntryPoint
import app.venues.shared.security.jwt.JwtAuthenticationFilter
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * Spring Security configuration for the Venues API application.
 *
 * This configuration establishes the security framework for the entire application,
 * including authentication, authorization, CORS policies, and session management.
 *
 * Security Strategy:
 * - Stateless JWT-based authentication (no server-side sessions)
 * - Dual authentication: Users and Staff have separate authentication flows
 * - Role-based access control (RBAC)
 * - BCrypt password encoding with strength 12
 * - CORS enabled for specified origins
 * - CSRF disabled (stateless API with JWT)
 *
 * Public endpoints (no authentication required):
 * - /api/v1/auth/user/ - User authentication (login, register)
 * - /api/v1/auth/staff/ - Staff authentication
 * - /api/v1/venues/ (GET only) - Public venue information
 * - /api/v1/events/ (GET only) - Public event information
 * - /api/v1/sessions/ (GET only) - Public seating charts
 * - /api/v1/checkout (POST) - Guest checkout
 * - /api/v1/bookings/ (GET) - Public booking retrieval
 * - /actuator/health - Health check endpoint
 *
 * Protected endpoints require valid JWT token in Authorization header.
 *
 * @property jwtAuthenticationFilter Custom filter for JWT token validation
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter
) {

    private val logger = KotlinLogging.logger {}

    /**
     * Configures the security filter chain with authentication and authorization rules.
     *
     * This method defines which endpoints are public vs. protected, configures CORS,
     * disables CSRF for stateless API, and sets up JWT authentication filter.
     *
     * @param http HttpSecurity builder for configuring web security
     * @return Configured SecurityFilterChain
     * @throws Exception if security configuration fails
     */
    @Bean
    @Throws(Exception::class)
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        logger.info { "Configuring Spring Security filter chain" }

        http
            // CSRF disabled - we're using JWT tokens in a stateless API
            .csrf { csrf -> csrf.disable() }

            // CORS configuration - apply custom CORS settings
            .cors { cors -> cors.configurationSource(corsConfigurationSource()) }

            // Authorization rules
            .authorizeHttpRequests { auth ->
                auth
                    // Public endpoints - no authentication required
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/user/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/staff/**").permitAll()

                    .requestMatchers(HttpMethod.GET, "/api/v1/venues/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/events/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/sessions/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/bookings/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/checkout").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/health/**").permitAll()
                    .requestMatchers("/actuator/**").permitAll()
                    .requestMatchers("/api/v1/public/**").permitAll()

                    // Swagger UI and OpenAPI endpoints
                    .requestMatchers("/v1/swagger-ui/**").permitAll()
                    .requestMatchers("/v1/swagger-ui.html").permitAll()
                    .requestMatchers("/v1/api-docs/**").permitAll()
                    .requestMatchers("/v1/api-docs").permitAll()

                    // All other endpoints require authentication
                    .anyRequest().authenticated()
            }

            // Session management - stateless (no sessions, JWT only)
            .sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }

            // Exception handling
            .exceptionHandling { exceptions ->
                exceptions
                    // Delegate to custom entry point for consistent error responses
                    .authenticationEntryPoint(JwtAuthenticationEntryPoint())
                    // Delegate to custom handler for consistent error responses
                    .accessDeniedHandler(JwtAccessDeniedHandler())
            }

        // Add JWT authentication filter before username/password authentication
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        val filterChain = http.build()
        logger.info { "Spring Security filter chain configured successfully" }

        return filterChain
    }

    /**
     * CORS configuration for cross-origin requests.
     *
     * Defines which origins, methods, and headers are allowed for CORS requests.
     * In production, this should be restricted to specific trusted origins.
     *
     * @return CorsConfigurationSource with allowed origins, methods, and headers
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        logger.info { "Configuring CORS policy" }

        val configuration = CorsConfiguration().apply {
            // Allowed origins - UPDATE IN PRODUCTION with specific domains
            //TODO: Restrict origins before deploying to production
            allowedOrigins = listOf(
                "http://localhost:3000",  // React/Vue dev server
                "http://localhost:5173",  // Vite dev server
                "http://localhost:4200",  // Angular dev server
                "http://localhost:8080"   // Same origin
            )

            // Allowed HTTP methods
            allowedMethods = listOf(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name()
            )

            // Allowed headers
            allowedHeaders = listOf(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Requested-With",
                "X-Correlation-ID",
                "X-Request-ID"
            )

            // Headers exposed to the client
            exposedHeaders = listOf(
                "Authorization",
                "X-Correlation-ID",
                "X-Request-ID"
            )

            // Allow credentials (cookies, authorization headers)
            allowCredentials = true

            // How long the browser should cache preflight requests (in seconds)
            maxAge = 3600L
        }

        val source = UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }

        logger.info { "CORS policy configured successfully" }
        return source
    }

    /**
     * Password encoder bean using BCrypt with strength 12.
     *
     * BCrypt is a secure hashing algorithm specifically designed for password storage.
     * Strength 12 provides a good balance between security and performance.
     *
     * @return BCryptPasswordEncoder instance with strength 12
     */
    @Bean
    fun passwordEncoder(): PasswordEncoder {
        logger.info { "Configuring BCrypt password encoder with strength 12" }
        return BCryptPasswordEncoder(12)
    }

    /**
     * Authentication manager bean for programmatic authentication.
     *
     * This bean is required for authentication operations outside the filter chain,
     * such as during login when validating user credentials.
     *
     * @param authConfig Authentication configuration
     * @return AuthenticationManager instance
     * @throws Exception if authentication manager cannot be created
     */
    @Bean
    @Throws(Exception::class)
    fun authenticationManager(authConfig: AuthenticationConfiguration): AuthenticationManager {
        logger.info { "Configuring authentication manager" }
        return authConfig.authenticationManager
    }
}
