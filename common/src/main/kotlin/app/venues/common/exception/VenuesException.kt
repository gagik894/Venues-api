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
 * @property httpStatus HTTP status code associated with this exception
 */
sealed class VenuesException(
    override val message: String,
    val errorCode: String,
    val httpStatus: Int,
    cause: Throwable? = null
) : RuntimeException(message, cause) {

    /**
     * Exception for business rule violations.
     * HTTP Status: 400 Bad Request
     */
    class BusinessRuleViolation(
        message: String = AppConstants.ErrorCode.BUSINESS_RULE_VIOLATION.message,
        errorCode: String = AppConstants.ErrorCode.BUSINESS_RULE_VIOLATION.code,
        cause: Throwable? = null,
        httpStatus: Int = AppConstants.ErrorCode.BUSINESS_RULE_VIOLATION.httpStatus
    ) : VenuesException(message, errorCode, httpStatus, cause)

    /**
     * Exception when a requested resource is not found.
     * HTTP Status: 404 Not Found
     */
    class ResourceNotFound(
        message: String = AppConstants.ErrorCode.NOT_FOUND.message,
        errorCode: String = AppConstants.ErrorCode.NOT_FOUND.code,
        cause: Throwable? = null,
        httpStatus: Int = AppConstants.ErrorCode.NOT_FOUND.httpStatus
    ) : VenuesException(message, errorCode, httpStatus, cause)

    /**
     * Exception for authentication failures.
     * HTTP Status: 401 Unauthorized
     */
    class AuthenticationFailure(
        message: String = AppConstants.ErrorCode.AUTHENTICATION_FAILED.message,
        errorCode: String = AppConstants.ErrorCode.AUTHENTICATION_FAILED.code,
        cause: Throwable? = null,
        httpStatus: Int = AppConstants.ErrorCode.AUTHENTICATION_FAILED.httpStatus
    ) : VenuesException(message, errorCode, httpStatus, cause)

    /**
     * Exception for authorization/permission failures.
     * HTTP Status: 403 Forbidden
     */
    class AuthorizationFailure(
        message: String = AppConstants.ErrorCode.AUTHORIZATION_FAILED.message,
        errorCode: String = AppConstants.ErrorCode.AUTHORIZATION_FAILED.code,
        cause: Throwable? = null,
        httpStatus: Int = AppConstants.ErrorCode.AUTHORIZATION_FAILED.httpStatus
    ) : VenuesException(message, errorCode, httpStatus, cause)

    /**
     * Exception for data validation failures.
     * HTTP Status: 422 Unprocessable Entity
     */
    class ValidationFailure(
        message: String = AppConstants.ErrorCode.VALIDATION_FAILED.message,
        errorCode: String = AppConstants.ErrorCode.VALIDATION_FAILED.code,
        val violations: Map<String, List<String>> = emptyMap(),
        cause: Throwable? = null,
        httpStatus: Int = AppConstants.ErrorCode.VALIDATION_FAILED.httpStatus
    ) : VenuesException(message, errorCode, httpStatus, cause)

    /**
     * Exception for conflicts with existing resources.
     * HTTP Status: 409 Conflict
     */
    class ResourceConflict(
        message: String = AppConstants.ErrorCode.RESOURCE_CONFLICT.message,
        errorCode: String = AppConstants.ErrorCode.RESOURCE_CONFLICT.code,
        cause: Throwable? = null,
        httpStatus: Int = AppConstants.ErrorCode.RESOURCE_CONFLICT.httpStatus
    ) : VenuesException(message, errorCode, httpStatus, cause)

    /**
     * Exception for internal system errors.
     * HTTP Status: 500 Internal Server Error
     */
    class InternalError(
        message: String = AppConstants.ErrorCode.INTERNAL_ERROR.message,
        errorCode: String = AppConstants.ErrorCode.INTERNAL_ERROR.code,
        cause: Throwable? = null,
        httpStatus: Int = AppConstants.ErrorCode.INTERNAL_ERROR.httpStatus
    ) : VenuesException(message, errorCode, httpStatus, cause)

    /**
     * Exception for external service failures.
     * HTTP Status: 502 Bad Gateway
     */
    class ExternalServiceFailure(
        message: String = AppConstants.ErrorCode.EXTERNAL_SERVICE_ERROR.message,
        errorCode: String = AppConstants.ErrorCode.EXTERNAL_SERVICE_ERROR.code,
        cause: Throwable? = null,
        httpStatus: Int = AppConstants.ErrorCode.EXTERNAL_SERVICE_ERROR.httpStatus
    ) : VenuesException(message, errorCode, httpStatus, cause)

    /**
     * Exception for rate limiting violations.
     * HTTP Status: 429 Too Many Requests
     */
    class RateLimitExceeded(
        message: String = AppConstants.ErrorCode.RATE_LIMIT_EXCEEDED.message,
        errorCode: String = AppConstants.ErrorCode.RATE_LIMIT_EXCEEDED.code,
        cause: Throwable? = null,
        httpStatus: Int = AppConstants.ErrorCode.RATE_LIMIT_EXCEEDED.httpStatus
    ) : VenuesException(message, errorCode, httpStatus, cause)
}
