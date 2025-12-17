package app.venues.shared.web.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import org.springframework.web.filter.CommonsRequestLoggingFilter
import java.text.SimpleDateFormat
import java.util.*

/**
 * General web configuration for the Venues API.
 *
 * This configuration class defines web-related beans and settings that
 * are used throughout the application, including:
 * - JSON serialization/deserialization (Jackson ObjectMapper)
 * - Bean validation
 * - Request logging
 *
 * All configurations follow Spring Boot best practices and government
 * security standards for production readiness.
 */
@Configuration
class WebConfig {

    private val logger = KotlinLogging.logger {}

    /**
     * Configures Jackson ObjectMapper for JSON processing.
     *
     * This customized ObjectMapper ensures consistent JSON serialization
     * and deserialization across the entire application. It includes:
     * - Kotlin module for proper Kotlin data class support
     * - Java 8 Time module for LocalDate, LocalDateTime, Instant, etc.
     * - ISO-8601 date format
     * - Pretty printing for development
     * - Sensible defaults for unknown properties and null handling
     *
     * @return Configured ObjectMapper instance
     */
    @Bean
    fun objectMapper(): ObjectMapper {
        logger.info { "Configuring Jackson ObjectMapper" }

        return Jackson2ObjectMapperBuilder()
            .apply {
                // Register Kotlin module for data classes, default parameters, etc.
                modulesToInstall(JavaTimeModule())

                // Date/Time formatting
                dateFormat(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ"))
                timeZone(TimeZone.getTimeZone("UTC"))

                // Serialization features
                featuresToEnable(
                    SerializationFeature.INDENT_OUTPUT  // Pretty print (disable in production if needed)
                )
                featuresToDisable(
                    SerializationFeature.FAIL_ON_EMPTY_BEANS,
                    SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
                )

                // Deserialization features
                featuresToEnable(
                    DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT
                )
                featuresToDisable(
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES
                )

                // Ensure default-valued fields are included (e.g., ApiResponse.success)
                serializationInclusion(JsonInclude.Include.ALWAYS)
            }
            .build<ObjectMapper>()
            .registerKotlinModule()
            .also {
                logger.info { "Jackson ObjectMapper configured successfully" }
            }
    }

    /**
     * Configures JSR-380 Bean Validation.
     *
     * This validator is used for validating request DTOs, domain objects,
     * and method parameters throughout the application.
     *
     * @return LocalValidatorFactoryBean for JSR-380 validation
     */
    @Bean
    fun validator(): LocalValidatorFactoryBean {
        logger.info { "Configuring JSR-380 Bean Validator" }
        return LocalValidatorFactoryBean()
    }

    /**
     * Configures request logging filter for debugging and audit purposes.
     *
     * This filter logs incoming HTTP requests including:
     * - Request URI
     * - HTTP method
     * - Query parameters
     * - Request headers (configurable)
     * - Client information
     *
     * In production, consider enabling/disabling based on environment
     * or configuring through application.properties.
     *
     * @return CommonsRequestLoggingFilter configured for detailed logging
     */
    @Bean
    fun requestLoggingFilter(): CommonsRequestLoggingFilter {
        logger.info { "Configuring HTTP request logging filter" }

        return CommonsRequestLoggingFilter().apply {
            setIncludeQueryString(true)
            setIncludePayload(true)
            setMaxPayloadLength(10000)
            setIncludeHeaders(true)
            setIncludeClientInfo(true)
            setAfterMessagePrefix("REQUEST DATA: ")
        }.also {
            logger.info { "HTTP request logging filter configured successfully" }
        }
    }
}

