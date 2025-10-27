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

    /**
     * Error codes for different types of failures.
     */
    object ErrorCodes {
        // General errors
        const val INTERNAL_ERROR = "ERR_INTERNAL_000"
        const val BAD_REQUEST = "ERR_BAD_REQUEST_001"
        const val NOT_FOUND = "ERR_NOT_FOUND_002"

        // Authentication & Authorization
        const val AUTHENTICATION_FAILED = "ERR_AUTH_100"
        const val AUTHORIZATION_FAILED = "ERR_AUTH_101"
        const val INVALID_TOKEN = "ERR_AUTH_102"
        const val TOKEN_EXPIRED = "ERR_AUTH_103"

        // Validation
        const val VALIDATION_FAILED = "ERR_VALIDATION_200"
        const val INVALID_INPUT = "ERR_VALIDATION_201"
        const val MISSING_REQUIRED_FIELD = "ERR_VALIDATION_202"

        // Business logic
        const val RESOURCE_CONFLICT = "ERR_BUSINESS_300"
        const val DUPLICATE_RESOURCE = "ERR_BUSINESS_301"
        const val BUSINESS_RULE_VIOLATION = "ERR_BUSINESS_302"

        // External services
        const val EXTERNAL_SERVICE_ERROR = "ERR_EXTERNAL_400"
        const val DATABASE_ERROR = "ERR_EXTERNAL_401"

        // Rate limiting
        const val RATE_LIMIT_EXCEEDED = "ERR_RATE_500"
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

