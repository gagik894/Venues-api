package app.venues.shared.security.config

import app.venues.shared.security.filter.RateLimitingFilter
import app.venues.shared.security.jwt.JwtAccessDeniedHandler
import app.venues.shared.security.jwt.JwtAuthenticationEntryPoint
import app.venues.shared.security.jwt.JwtAuthenticationFilter
import app.venues.shared.web.filter.DomainContextFilter
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.Filter
import org.springframework.beans.factory.ObjectProvider
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
 * - Role-based access control (RBAC) with @PreAuthorize annotations
 * - BCrypt password encoding with strength 12
 * - CORS enabled for specified origins
 * - CSRF disabled (stateless API with JWT)
 * - Granular path-based security (specific public endpoints only)
 *
 * Public Endpoints (No Authentication Required):
 *
 * Authentication:
 * - POST /api/v1/auth/user/ - User authentication (login, register)
 * - POST /api/v1/auth/staff/ - Staff authentication
 *
 * Public Browsing (GET only):
 * - GET /api/v1/venues - List venues
 * - GET /api/v1/venues/{id} - View venue details
 * - GET /api/v1/venues/{id}/website - View venue public website
 * - GET /api/v1/events - List events
 * - GET /api/v1/events/{id} - View event details
 * - GET /api/v1/events/{id}/sessions - View event sessions
 * - GET /api/v1/event/categories - List event categories
 * - GET /api/v1/sessions/{id} - View session details
 * - GET /api/v1/sessions/{id}/seating - View seating availability
 * - GET /api/v1/locations/ - Location data
 *
 * Cart & Checkout:
 * - ALL /api/v1/cart/ - Cart operations (guest + authenticated)
 * - POST /api/v1/checkout - Guest checkout
 * - GET /api/v1/bookings/{id} - View booking details
 *
 * System:
 * - GET /api/v1/health/ - Health checks
 * - /actuator/ - Spring Actuator endpoints
 *
 * Protected Endpoints (Authentication Required):
 * - /api/v1/venues/{id}/seating-charts - Venue seating management (STAFF)
 * - /api/v1/venues/{id}/events - Venue event management (STAFF)
 * - /api/v1/admin/ - Administrative operations (SUPER_ADMIN)
 * - All POST/PUT/PATCH/DELETE operations (except auth & checkout)
 *
 * @property jwtAuthenticationFilter Custom filter for JWT token validation
 *
 **/
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val jwtAuthenticationEntryPoint: JwtAuthenticationEntryPoint,
    private val jwtAccessDeniedHandler: JwtAccessDeniedHandler,
    private val domainContextFilter: DomainContextFilter,
    private val rateLimitingFilter: RateLimitingFilter,
    private val auditLoggingFilter: ObjectProvider<Filter>
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
                    // ============================================
                    // AUTHENTICATION ENDPOINTS (Public)
                    // ============================================
                    .requestMatchers(HttpMethod.POST, "/api/v1/user/auth/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/staff/auth/**").permitAll()
                    // ============================================
                    // PUBLIC VENUE BROWSING (Read-only)
                    // ============================================
                    // Public venue list and details
                    .requestMatchers(HttpMethod.GET, "/api/v1/venues").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/venues/domains").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/venues/{id}").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/venues/slug/{slug}").permitAll()

                    // White-label website (public read-only)
                    // Admin operations at /api/v1/venues/{id}/website/** require authentication
                    .requestMatchers(HttpMethod.GET, "/api/v1/venue/website/**").permitAll()

                    // ⚠️ IMPORTANT: All other /api/v1/venues/** endpoints are PROTECTED
                    // Includes: website management, seating-charts, events, etc.

                    .requestMatchers(HttpMethod.GET, "/api/v1/seating-charts/{chartId}/structure").permitAll()
                    // ============================================
                    // PUBLIC EVENT BROWSING (Read-only)
                    // ============================================
                    // Public event browsing and details
                    .requestMatchers(HttpMethod.GET, "/api/v1/events").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/events/{id}").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/events/search").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/events/{id}/sessions").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/events/{id}/seating").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/events/venue/**").permitAll()

                    // Event categories (public)
                    .requestMatchers(HttpMethod.GET, "/api/v1/event/categories/**").permitAll()

                    // ⚠️ IMPORTANT: All other /api/v1/events/** endpoints are PROTECTED

                    // ============================================
                    // PUBLIC SESSION & SEATING (Read-only for customers)
                    // ============================================
                    .requestMatchers(HttpMethod.GET, "/api/v1/sessions/{id}").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/sessions/{id}/seating").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/sessions/{id}/inventory").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/sessions/{id}/availability").permitAll()

                    // ============================================
                    // CART & CHECKOUT (Guest + Authenticated)
                    // ============================================
                    .requestMatchers("/api/v1/cart/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/checkout").permitAll()

                    // ============================================
                    // BOOKINGS (Guest + Authenticated)
                    // ============================================
                    .requestMatchers(HttpMethod.GET, "/api/v1/bookings/{id}").permitAll()

                    // ============================================
                    // LOCATIONS (Public)
                    // ============================================
                    .requestMatchers(HttpMethod.GET, "/api/v1/locations/**").permitAll()

                    // ============================================
                    // PUBLIC CONFIG (White-label)
                    // ============================================
                    // legacy path removed; new site endpoints handled above

                    // ============================================
                    // HEALTH & MONITORING
                    // ============================================
                    .requestMatchers(HttpMethod.GET, "/api/v1/health/**").permitAll()
                    .requestMatchers("/actuator/**").permitAll()
                    .requestMatchers("/api/test/**").permitAll()
                    // ============================================
                    // SWAGGER / API DOCUMENTATION
                    // ============================================
                    .requestMatchers("/v1/swagger-ui/**").permitAll()
                    .requestMatchers("/v1/swagger-ui.html").permitAll()
                    .requestMatchers("/v1/api-docs/**").permitAll()
                    .requestMatchers("/v1/api-docs").permitAll()

                    // ============================================
                    // ALL OTHER ENDPOINTS REQUIRE AUTHENTICATION
                    // ============================================
                    // This includes:
                    // - /api/v1/admin/** (Super Admin)
                    // - /api/v1/venues/{id}/seating-charts (Staff)
                    // - /api/v1/venues/{id}/events (Management)
                    // - All POST/PUT/PATCH/DELETE operations
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
                    .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                    // Delegate to custom handler for consistent error responses
                    .accessDeniedHandler(jwtAccessDeniedHandler)
            }

        // Add rate limiting filter at the very beginning
        http.addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter::class.java)
        // Add domain context filter before JWT (to resolve domain before auth)
        http.addFilterBefore(domainContextFilter, UsernamePasswordAuthenticationFilter::class.java)
        // Add JWT authentication filter before username/password authentication
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
        // Add audit logging after JWT so staffId is available
        auditLoggingFilter.ifAvailable { filter ->
            http.addFilterAfter(filter, UsernamePasswordAuthenticationFilter::class.java)
        }

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
            // Allowed origin PATTERNS - supports wildcards for subdomains
            // Pattern format: https://*.armtick.am matches all subdomains
            val allowedOriginsEnv = System.getenv("ALLOWED_ORIGINS")

            // Always allow all armtick.am subdomains
            val defaultPatterns = listOf(
                "https://*.armtick.am",    // All subdomains (admin, staff, etc.)
                "https://armtick.am"       // Root domain
            )

            // Add any explicit 3rd party origins from environment
            val additionalOrigins = allowedOriginsEnv?.split(",")?.map { it.trim() }
                ?: // Development defaults
                listOf(
                    "http://localhost:3000",  // React/Vue dev server
                    "http://localhost:5173",  // Vite dev server
                    "http://localhost:4200",  // Angular dev server
                    "http://localhost:8080"   // Same origin
                )

            // Use allowedOriginPatterns for wildcard support (works with credentials)
            allowedOriginPatterns = defaultPatterns + additionalOrigins

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
                "X-Request-ID",
                "X-Venue-Domain"  // White-label domain header
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
