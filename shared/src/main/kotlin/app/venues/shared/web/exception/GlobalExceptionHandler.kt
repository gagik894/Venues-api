package app.venues.shared.web.exception

import app.venues.common.constants.AppConstants
import app.venues.common.exception.VenuesException
import app.venues.common.model.ApiErrorResponse
import app.venues.common.model.ErrorDetail
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.AuthenticationException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.NoHandlerFoundException
import java.util.UUID

/**
 * Global exception handler for all REST API controllers.
 *
 * This centralized exception handling mechanism ensures consistent error responses
 * across the entire API. It catches exceptions thrown from any controller,
 * logs them appropriately, and returns standardized error responses to clients.
 *
 * Exception Handling Strategy:
 * - Application-specific exceptions (VenuesException) are mapped to appropriate HTTP status codes
 * - Framework exceptions (Spring, validation) are handled with detailed error messages
 * - Security exceptions are handled separately to prevent information leakage
 * - All exceptions are logged with correlation IDs for traceability
 * - Sensitive information is never exposed to clients
 *
 * Response Format:
 * All error responses follow the ApiErrorResponse structure defined in the common module,
 * ensuring consistency and predictability for API clients.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = KotlinLogging.logger {}

    /**
     * Handles custom application-specific exceptions (VenuesException hierarchy).
     *
     * Maps each VenuesException type to the appropriate HTTP status code
     * and returns a standardized error response.
     *
     * @param ex The VenuesException that was thrown
     * @param request The HTTP request that caused the exception
     * @return ResponseEntity with ApiErrorResponse and appropriate HTTP status
     */
    @ExceptionHandler(VenuesException::class)
    fun handleVenuesException(
        ex: VenuesException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {
        val traceId = generateTraceId()

        val status = when (ex) {
            is VenuesException.ResourceNotFound -> HttpStatus.NOT_FOUND
            is VenuesException.AuthenticationFailure -> HttpStatus.UNAUTHORIZED
            is VenuesException.AuthorizationFailure -> HttpStatus.FORBIDDEN
            is VenuesException.ValidationFailure -> HttpStatus.UNPROCESSABLE_ENTITY
            is VenuesException.ResourceConflict -> HttpStatus.CONFLICT
            is VenuesException.BusinessRuleViolation -> HttpStatus.BAD_REQUEST
            is VenuesException.RateLimitExceeded -> HttpStatus.TOO_MANY_REQUESTS
            is VenuesException.ExternalServiceFailure -> HttpStatus.BAD_GATEWAY
            is VenuesException.InternalError -> HttpStatus.INTERNAL_SERVER_ERROR
        }

        logger.warn { "[$traceId] VenuesException occurred: ${ex.message}" }

        val details = if (ex is VenuesException.ValidationFailure) ex.violations else null

        val errorResponse = ApiErrorResponse(
            success = false,
            error = ErrorDetail(
                code = ex.errorCode,
                message = ex.message,
                details = details
            ),
            timestamp = kotlinx.datetime.Clock.System.now().toString(),
            path = request.requestURI,
            traceId = traceId
        )

        return ResponseEntity.status(status).body(errorResponse)
    }

    /**
     * Handles validation exceptions from @Valid or @Validated annotations.
     *
     * Extracts field-level validation errors and returns them in a structured format
     * so clients can identify exactly which fields failed validation and why.
     *
     * @param ex The MethodArgumentNotValidException containing validation errors
     * @param request The HTTP request that caused the exception
     * @return ResponseEntity with ApiErrorResponse containing validation details
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {
        val traceId = generateTraceId()

        logger.warn { "[$traceId] Validation failed: ${ex.bindingResult.errorCount} errors" }

        // Extract field errors into a structured map
        val violations = ex.bindingResult.allErrors
            .groupBy { error ->
                when (error) {
                    is FieldError -> error.field
                    else -> error.objectName
                }
            }
            .mapValues { (_, errors) ->
                errors.mapNotNull { it.defaultMessage }
            }

        val errorResponse = ApiErrorResponse(
            success = false,
            error = ErrorDetail(
                code = "VALIDATION_FAILED",
                message = "Validation failed for one or more fields",
                details = violations
            ),
            timestamp = kotlinx.datetime.Clock.System.now().toString(),
            path = request.requestURI,
            traceId = traceId
        )

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(errorResponse)
    }

    /**
     * Handles constraint violation exceptions from JSR-380 validation.
     *
     * @param ex The ConstraintViolationException containing constraint violations
     * @param request The HTTP request that caused the exception
     * @return ResponseEntity with ApiErrorResponse containing violation details
     */
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(
        ex: ConstraintViolationException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {
        val traceId = generateTraceId()

        logger.warn { "[$traceId] Constraint violation: ${ex.message}" }

        val violations = ex.constraintViolations
            .groupBy { it.propertyPath.toString() }
            .mapValues { (_, violations) -> violations.map { it.message } }

        val errorResponse = ApiErrorResponse(
            success = false,
            error = ErrorDetail(
                code = "CONSTRAINT_VIOLATION",
                message = "Constraint violation occurred",
                details = violations
            ),
            timestamp = kotlinx.datetime.Clock.System.now().toString(),
            path = request.requestURI,
            traceId = traceId
        )

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    /**
     * Handles Spring Security authentication exceptions.
     *
     * Returns 401 Unauthorized for authentication failures without exposing
     * sensitive security information.
     *
     * @param ex The AuthenticationException that was thrown
     * @param request The HTTP request that caused the exception
     * @return ResponseEntity with ApiErrorResponse
     */
    @ExceptionHandler(AuthenticationException::class, BadCredentialsException::class)
    fun handleAuthenticationException(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {
        val traceId = generateTraceId()

        logger.warn { "[$traceId] Authentication failed: ${ex.message}" }

        val errorResponse = ApiErrorResponse(
            success = false,
            error = ErrorDetail(
                code = "AUTHENTICATION_FAILED",
                message = "Authentication failed. Please check your credentials."
            ),
            timestamp = kotlinx.datetime.Clock.System.now().toString(),
            path = request.requestURI,
            traceId = traceId
        )

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse)
    }

    /**
     * Handles Spring Security authorization exceptions.
     *
     * Returns 403 Forbidden when user doesn't have required permissions.
     *
     * @param ex The AccessDeniedException that was thrown
     * @param request The HTTP request that caused the exception
     * @return ResponseEntity with ApiErrorResponse
     */
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(
        ex: AccessDeniedException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {
        val traceId = generateTraceId()

        logger.warn { "[$traceId] Access denied: ${ex.message}" }

        val errorResponse = ApiErrorResponse(
            success = false,
            error = ErrorDetail(
                code = "ACCESS_DENIED",
                message = "You don't have permission to access this resource."
            ),
            timestamp = kotlinx.datetime.Clock.System.now().toString(),
            path = request.requestURI,
            traceId = traceId
        )

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse)
    }

    /**
     * Handles malformed JSON or request body parsing errors.
     *
     * @param ex The HttpMessageNotReadableException that was thrown
     * @param request The HTTP request that caused the exception
     * @return ResponseEntity with ApiErrorResponse
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadable(
        ex: HttpMessageNotReadableException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {
        val traceId = generateTraceId()

        logger.warn { "[$traceId] Malformed request body: ${ex.message}" }

        val errorResponse = ApiErrorResponse(
            success = false,
            error = ErrorDetail(
                code = "MALFORMED_REQUEST",
                message = "Request body is malformed or contains invalid data"
            ),
            timestamp = kotlinx.datetime.Clock.System.now().toString(),
            path = request.requestURI,
            traceId = traceId
        )

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    /**
     * Handles missing required request parameters.
     *
     * @param ex The MissingServletRequestParameterException that was thrown
     * @param request The HTTP request that caused the exception
     * @return ResponseEntity with ApiErrorResponse
     */
    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingRequestParameter(
        ex: MissingServletRequestParameterException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {
        val traceId = generateTraceId()

        logger.warn { "[$traceId] Missing request parameter: ${ex.parameterName}" }

        val errorResponse = ApiErrorResponse(
            success = false,
            error = ErrorDetail(
                code = "MISSING_PARAMETER",
                message = "Required request parameter '${ex.parameterName}' is missing"
            ),
            timestamp = kotlinx.datetime.Clock.System.now().toString(),
            path = request.requestURI,
            traceId = traceId
        )

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    /**
     * Handles type mismatch in request parameters.
     *
     * @param ex The MethodArgumentTypeMismatchException that was thrown
     * @param request The HTTP request that caused the exception
     * @return ResponseEntity with ApiErrorResponse
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(
        ex: MethodArgumentTypeMismatchException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {
        val traceId = generateTraceId()

        logger.warn { "[$traceId] Type mismatch for parameter: ${ex.name}" }

        val errorResponse = ApiErrorResponse(
            success = false,
            error = ErrorDetail(
                code = "INVALID_PARAMETER_TYPE",
                message = "Parameter '${ex.name}' has invalid type. Expected: ${ex.requiredType?.simpleName}"
            ),
            timestamp = kotlinx.datetime.Clock.System.now().toString(),
            path = request.requestURI,
            traceId = traceId
        )

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    /**
     * Handles 404 Not Found when no handler is found for the request.
     *
     * @param ex The NoHandlerFoundException that was thrown
     * @param request The HTTP request that caused the exception
     * @return ResponseEntity with ApiErrorResponse
     */
    @ExceptionHandler(NoHandlerFoundException::class)
    fun handleNoHandlerFound(
        ex: NoHandlerFoundException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {
        val traceId = generateTraceId()

        logger.warn { "[$traceId] No handler found for: ${ex.httpMethod} ${ex.requestURL}" }

        val errorResponse = ApiErrorResponse(
            success = false,
            error = ErrorDetail(
                code = "ENDPOINT_NOT_FOUND",
                message = "The requested endpoint does not exist"
            ),
            timestamp = kotlinx.datetime.Clock.System.now().toString(),
            path = request.requestURI,
            traceId = traceId
        )

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse)
    }

    /**
     * Handles all other uncaught exceptions.
     *
     * This is the catch-all handler for any exception not explicitly handled above.
     * Returns 500 Internal Server Error without exposing internal details.
     *
     * @param ex The exception that was thrown
     * @param request The HTTP request that caused the exception
     * @return ResponseEntity with ApiErrorResponse
     */
    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {
        val traceId = generateTraceId()

        logger.error(ex) { "[$traceId] Unexpected error occurred: ${ex.message}" }

        val errorResponse = ApiErrorResponse(
            success = false,
            error = ErrorDetail(
                code = "INTERNAL_ERROR",
                message = "An unexpected error occurred. Please try again later."
            ),
            timestamp = kotlinx.datetime.Clock.System.now().toString(),
            path = request.requestURI,
            traceId = traceId
        )

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }

    /**
     * Generates a unique trace ID for error tracking.
     *
     * This trace ID can be used to correlate logs, track errors through
     * monitoring systems, and help with debugging in production.
     *
     * @return UUID-based trace identifier
     */
    private fun generateTraceId(): String = UUID.randomUUID().toString()
}
