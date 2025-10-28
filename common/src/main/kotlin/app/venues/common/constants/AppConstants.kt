package app.venues.common.constants

/**
 * Application-wide constants used across all modules.
 *
 * This object serves as a single source of truth for constant values
 * that need to be consistent throughout the application.
 */
object AppConstants {

    /**
     * Application information constants.
     */
    object Application {
        const val NAME = "Venues API"
        const val DESCRIPTION = "Government-Sponsored Cultural Venues Portal Backend"
        const val VERSION = "1.0.0"
    }

    /**
     * service status constants.
     */
    object Status {
        const val UP = "UP"
        const val DOWN = "DOWN"
    }

    /**
     * Date and time format constants.
     */
    object DateTimeFormat {
        const val ISO_8601 = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
        const val DATE_ONLY = "yyyy-MM-dd"
        const val TIME_ONLY = "HH:mm:ss"
        const val DISPLAY_FORMAT = "dd/MM/yyyy HH:mm"
    }

    /**
     * Pagination default values.
     */
    object Pagination {
        const val DEFAULT_PAGE = 0
        const val DEFAULT_SIZE = 20
        const val MAX_SIZE = 100
        const val MIN_SIZE = 1
    }

    /**
     * Validation constraint constants.
     */
    object Validation {
        // String length constraints
        const val MIN_NAME_LENGTH = 2
        const val MAX_NAME_LENGTH = 100
        const val MAX_DESCRIPTION_LENGTH = 2000
        const val MAX_ADDRESS_LENGTH = 255
        const val MIN_PASSWORD_LENGTH = 8
        const val MAX_PASSWORD_LENGTH = 128

        // Numeric constraints
        const val MIN_CAPACITY = 1
        const val MAX_CAPACITY = 1_000_000
        const val MIN_PRICE = 0.0
        const val MAX_PRICE = 1_000_000.0
    }

    /**
     * HTTP header names.
     */
    object Headers {
        const val AUTHORIZATION = "Authorization"
        const val BEARER_PREFIX = "Bearer "
        const val CONTENT_TYPE = "Content-Type"
        const val ACCEPT = "Accept"
        const val CORRELATION_ID = "X-Correlation-ID"
        const val REQUEST_ID = "X-Request-ID"
        const val API_VERSION = "X-API-Version"
    }

    /**
     * Security-related constants.
     */
    object Security {
        const val JWT_TOKEN_PREFIX = "Bearer "
        const val JWT_HEADER_NAME = "Authorization"
        const val TOKEN_EXPIRATION_HOURS = 24L
        const val REFRESH_TOKEN_EXPIRATION_DAYS = 30L
        const val MAX_LOGIN_ATTEMPTS = 5
        const val LOCKOUT_DURATION_MINUTES = 30L
    }

    /**
     * Cache-related constants.
     */
    object Cache {
        const val DEFAULT_TTL_MINUTES = 30L
        const val VENUES_CACHE_NAME = "venues"
        const val EVENTS_CACHE_NAME = "events"
        const val USERS_CACHE_NAME = "users"
    }

    enum class ErrorCode(val code: String, val httpStatus: Int, val message: String) {
        // General
        INTERNAL_ERROR("INTERNAL_ERROR", 500, "An unexpected error occurred. Please try again later"),
        BAD_REQUEST("BAD_REQUEST", 400, "The request could not be understood or was missing required parameters"),
        NOT_FOUND("NOT_FOUND", 404, "The requested resource was not found"),
        ENDPOINT_NOT_FOUND("ENDPOINT_NOT_FOUND", 404, "The requested endpoint does not exist"),

        // Auth
        AUTHENTICATION_FAILED("AUTHENTICATION_FAILED", 401, "Authentication failed. Please check your credentials"),
        AUTHORIZATION_FAILED("AUTHORIZATION_FAILED", 403, "You do not have permission to access this resource"),
        INVALID_TOKEN("INVALID_TOKEN", 401, "The provided authentication token is invalid"),
        TOKEN_EXPIRED("TOKEN_EXPIRED", 401, "Your session has expired. Please log in again"),

        // Validation
        VALIDATION_FAILED("VALIDATION_FAILED", 422, "Validation failed for one or more fields"),
        INVALID_INPUT("INVALID_INPUT", 422, "One or more input values are invalid"),
        MISSING_REQUIRED_FIELD("MISSING_REQUIRED_FIELD", 422, "Required field is missing"),
        INVALID_PARAMETER_TYPE(
            "INVALID_PARAMETER_TYPE",
            400,
            "Parameter '\${parameterName}' has an invalid type. Expected: \${expectedType}"
        ),
        MALFORMED_REQUEST("MALFORMED_REQUEST", 400, "The request is malformed or contains invalid syntax"),
        MISSING_PARAMETERS("MISSING_PARAMETERS", 400, "Required request parameter '\${parameterName}' is missing"),

        // Business logic
        RESOURCE_CONFLICT("RESOURCE_CONFLICT", 409, "The operation conflicts with the current state of the resource"),
        DUPLICATE_RESOURCE("DUPLICATE_RESOURCE", 409, "A resource with the same identifier already exists"),
        BUSINESS_RULE_VIOLATION("BUSINESS_RULE_VIOLATION", 422, "The operation violates business rules"),

        // External services
        EXTERNAL_SERVICE_ERROR("EXTERNAL_SERVICE_ERROR", 502, "An external service is currently unavailable"),
        DATABASE_ERROR("DATABASE_ERROR", 500, "A database error occurred while processing your request"),

        // Rate limiting
        RATE_LIMIT_EXCEEDED("RATE_LIMIT_EXCEEDED", 429, "Too many requests. Please try again later");

        fun formatMessage(vararg params: Pair<String, Any>): String {
            var result = message
            params.forEach { (key, value) ->
                result = result.replace("\${$key}", value.toString())
            }
            return result
        }
    }

    /**
     * Regex patterns for validation.
     */
    object Patterns {
        const val EMAIL = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
        const val PHONE = "^[+]?[0-9]{1,4}?[-\\s]?[(]?[0-9]{1,4}[)]?[-\\s]?[0-9]{1,4}[-\\s]?[0-9]{1,9}$"
        const val URL = "^https?://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$"
        const val UUID = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
        const val SLUG = "^[a-z0-9-]+$"
    }
}

