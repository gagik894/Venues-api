/*
 * Venues API - Government-Sponsored Cultural Venues Portal
 * Copyright (c) 2025 Government Cultural Department
 *
 * Base exception hierarchy for the Venues API system.
 * All custom exceptions in the application should extend from VenuesException.
 */

package app.venues.common.exception

import app.venues.common.constants.AppConstants

/**
 * Base exception class for all custom exceptions in the Venues API system.
 *
 * This sealed class hierarchy provides a type-safe way to handle different
 * categories of exceptions throughout the application. Each exception type
 * carries an error code for client consumption and proper logging.
 *
 * @property message Human-readable error message
 * @property errorCode Application-specific error code for API responses
 * @property cause The underlying cause of this exception, if any
 */
sealed class VenuesException(
    override val message: String,
    val errorCode: String,
    cause: Throwable? = null
) : RuntimeException(message, cause) {

    /**
     * Exception for business rule violations.
     * HTTP Status: 400 Bad Request
     */
    class BusinessRuleViolation(
        message: String,
        errorCode: String = AppConstants.ErrorCodes.BUSINESS_RULE_VIOLATION,
        cause: Throwable? = null
    ) : VenuesException(message, errorCode, cause)

    /**
     * Exception when a requested resource is not found.
     * HTTP Status: 404 Not Found
     */
    class ResourceNotFound(
        message: String,
        errorCode: String = AppConstants.ErrorCodes.NOT_FOUND,
        cause: Throwable? = null
    ) : VenuesException(message, errorCode, cause)

    /**
     * Exception for authentication failures.
     * HTTP Status: 401 Unauthorized
     */
    class AuthenticationFailure(
        message: String,
        errorCode: String = AppConstants.ErrorCodes.AUTHENTICATION_FAILED,
        cause: Throwable? = null
    ) : VenuesException(message, errorCode, cause)

    /**
     * Exception for authorization/permission failures.
     * HTTP Status: 403 Forbidden
     */
    class AuthorizationFailure(
        message: String,
        errorCode: String = AppConstants.ErrorCodes.AUTHORIZATION_FAILED,
        cause: Throwable? = null
    ) : VenuesException(message, errorCode, cause)

    /**
     * Exception for data validation failures.
     * HTTP Status: 422 Unprocessable Entity
     */
    class ValidationFailure(
        message: String,
        errorCode: String = AppConstants.ErrorCodes.VALIDATION_FAILED,
        val violations: Map<String, List<String>> = emptyMap(),
        cause: Throwable? = null
    ) : VenuesException(message, errorCode, cause)

    /**
     * Exception for conflicts with existing resources.
     * HTTP Status: 409 Conflict
     */
    class ResourceConflict(
        message: String,
        errorCode: String = AppConstants.ErrorCodes.RESOURCE_CONFLICT,
        cause: Throwable? = null
    ) : VenuesException(message, errorCode, cause)

    /**
     * Exception for internal system errors.
     * HTTP Status: 500 Internal Server Error
     */
    class InternalError(
        message: String,
        errorCode: String = AppConstants.ErrorCodes.INTERNAL_ERROR,
        cause: Throwable? = null
    ) : VenuesException(message, errorCode, cause)

    /**
     * Exception for external service failures.
     * HTTP Status: 502 Bad Gateway
     */
    class ExternalServiceFailure(
        message: String,
        errorCode: String = AppConstants.ErrorCodes.EXTERNAL_SERVICE_ERROR,
        cause: Throwable? = null
    ) : VenuesException(message, errorCode, cause)

    /**
     * Exception for rate limiting violations.
     * HTTP Status: 429 Too Many Requests
     */
    class RateLimitExceeded(
        message: String,
        errorCode: String = AppConstants.ErrorCodes.RATE_LIMIT_EXCEEDED,
        cause: Throwable? = null
    ) : VenuesException(message, errorCode, cause)
}

