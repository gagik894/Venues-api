package app.venues.shared.web.config

import app.venues.common.constants.AppConstants
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI 3.0 documentation configuration for the Venues API.
 *
 * This configuration generates interactive API documentation using Swagger UI,
 * which is essential for:
 * - Developer onboarding and API exploration
 * - Client integration and testing
 * - Government transparency and public API documentation
 * - Automated API testing and validation
 *
 * The documentation includes:
 * - Comprehensive API information (title, description, version, contact)
 * - Security scheme definitions (JWT Bearer token for users and venues)
 * - Server configurations (dev, staging, production)
 * - Detailed endpoint descriptions with request/response examples
 *
 * Access the documentation at:
 * - Swagger UI: http://localhost:8080/v1/swagger-ui.html
 * - OpenAPI JSON: http://localhost:8080/v1/api-docs
 */
@Configuration
class OpenApiConfig {

    private val logger = KotlinLogging.logger {}

    @Value("\${spring.application.name:Venues API}")
    private lateinit var applicationName: String

    @Value("\${server.port:8080}")
    private lateinit var serverPort: String

    /**
     * Configures OpenAPI documentation bean.
     *
     * Creates a comprehensive API documentation including metadata,
     * security schemes, and server configurations.
     *
     * @return Configured OpenAPI instance
     */
    @Bean
    fun openAPI(): OpenAPI {
        logger.info { "Configuring OpenAPI documentation" }

        val securitySchemeName = "bearerAuth"

        return OpenAPI()
            .info(apiInfo())
            .servers(listOf(
                Server().apply {
                    url = "http://localhost:$serverPort"
                    description = "Local Development Server"
                },
                Server().apply {
                    url = "https://staging-api.venues.gov"
                    description = "Staging Server"
                },
                Server().apply {
                    url = "https://api.venues.gov"
                    description = "Production Server"
                }
            ))
            .components(
                Components()
                    .addSecuritySchemes(
                        securitySchemeName,
                        SecurityScheme()
                            .name(securitySchemeName)
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description("""
                                Enter JWT token obtained from authentication endpoints:
                                - User authentication: /api/v1/auth/user/login
                                - Venue authentication: /api/v1/auth/venue/login
                            """.trimIndent())
                    )
            )
            .addSecurityItem(SecurityRequirement().addList(securitySchemeName))
            .also {
                logger.info { "OpenAPI documentation configured successfully" }
            }
    }

    /**
     * Creates API information metadata.
     *
     * Provides comprehensive information about the API including:
     * - Title and description
     * - Version number
     * - Contact information
     * - License information
     * - Terms of service
     *
     * @return Info object with API metadata
     */
    private fun apiInfo(): Info {
        return Info()
            .title("Venues API")
            .description("""
                # Government-Sponsored Cultural Venues Portal - Backend API
                
                ## Overview
                This API provides comprehensive access to cultural venues (theaters, opera houses, concert halls, etc.) 
                and their events across the nation. It serves as the backend for the public-facing portal where 
                citizens can discover cultural events, view venue information, and (in future releases) purchase tickets.
                
                ## Key Features
                - **Venue Management**: Browse and search cultural venues by location, type, and capacity
                - **Event Discovery**: Find upcoming events with detailed information and availability
                - **User Management**: User registration, authentication, and profile management
                - **Venue Portal**: Venue owners can manage their venues and events
                - **Booking System**: (Future) Reserve and purchase tickets for events
                - **Admin Portal**: (Future) System administration
                
                ## Authentication
                The API supports two types of authentication:
                
                ### User Authentication
                For regular users (citizens browsing and booking):
                1. Register via `/api/v1/auth/user/register`
                2. Login via `/api/v1/auth/user/login` to receive a JWT token
                3. Include the token in the Authorization header: `Bearer <token>`
                
                ### Venue Authentication
                For venue owners and managers:
                1. Register your venue via `/api/v1/auth/venue/register`
                2. Login via `/api/v1/auth/venue/login` to receive a JWT token
                3. Include the token in the Authorization header: `Bearer <token>`
                
                ## Rate Limiting
                API requests are rate-limited to ensure fair usage and system stability:
                - Public endpoints: 100 requests per minute
                - Authenticated endpoints: 1000 requests per minute
                
                ## Support
                For API support and questions, contact the development team via the contact information below.
            """.trimIndent())
            .version("1.0.0")
            .contact(
                Contact()
                    .name("Government Cultural Department - IT Division")
                    .email("api-support@venues.gov")
                    .url("https://www.venues.gov/support")
            )
            .license(
                License()
                    .name("Government Public License v1.0")
                    .url("https://www.venues.gov/license")
            )
            .termsOfService("https://www.venues.gov/terms")
    }
}
