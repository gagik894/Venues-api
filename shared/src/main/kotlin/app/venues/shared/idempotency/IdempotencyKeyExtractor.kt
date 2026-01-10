package app.venues.shared.idempotency

import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import kotlin.reflect.jvm.kotlinFunction

/**
 * Utility for extracting idempotency-related values from controller method parameters.
 *
 * Provides a centralized, tested, and maintainable approach to parameter extraction
 * from Spring MVC annotations, eliminating code duplication and fragility.
 *
 * This class follows the Single Responsibility Principle by handling only
 * parameter extraction logic, separated from the core idempotency mechanism.
 */
object IdempotencyKeyExtractor {

    private const val IDEMPOTENCY_HEADER_NAME = "Idempotency-Key"

    /**
     * Extract the Idempotency-Key header value from method parameters.
     *
     * Searches for @RequestHeader("Idempotency-Key") annotation.
     *
     * @param method Controller method to inspect
     * @param args Method arguments
     * @return Idempotency key if found, null otherwise
     */
    fun extractIdempotencyKey(method: Method, args: Array<Any>): String? {
        return extractHeaderValue(method, args, IDEMPOTENCY_HEADER_NAME)
    }

    /**
     * Extract a header value from method parameters.
     *
     * @param method Controller method to inspect
     * @param args Method arguments
     * @param headerName Header name to search for
     * @return Header value if found, null otherwise
     */
    fun extractHeaderValue(method: Method, args: Array<Any>, headerName: String): String? {
        val kParams = method.kotlinFunction?.parameters

        return method.parameters.indices.firstNotNullOfOrNull { index ->
            val param = method.parameters[index]
            val header = param.getAnnotation(RequestHeader::class.java) ?: return@firstNotNullOfOrNull null

            val extractedHeaderName = getAnnotationValue(header.name, header.value, param, kParams, index)

            if (extractedHeaderName.equals(headerName, ignoreCase = true)) {
                args.getOrNull(index) as? String
            } else {
                null
            }
        }
    }

    /**
     * Extract a request parameter value from method parameters.
     *
     * @param method Controller method to inspect
     * @param args Method arguments
     * @param paramName Parameter name to search for
     * @return Parameter value as String if found, null otherwise
     */
    fun extractRequestParam(method: Method, args: Array<Any>, paramName: String): String? {
        val kParams = method.kotlinFunction?.parameters

        return method.parameters.indices.firstNotNullOfOrNull { index ->
            val param = method.parameters[index]
            val requestParam = param.getAnnotation(RequestParam::class.java) ?: return@firstNotNullOfOrNull null

            val extractedParamName = getAnnotationValue(requestParam.name, requestParam.value, param, kParams, index)

            if (extractedParamName == paramName) {
                args.getOrNull(index)?.toString()
            } else {
                null
            }
        }
    }

    /**
     * Extract a path variable value from method parameters.
     *
     * @param method Controller method to inspect
     * @param args Method arguments
     * @param variableName Path variable name to search for
     * @return Variable value as String if found, null otherwise
     */
    fun extractPathVariable(method: Method, args: Array<Any>, variableName: String): String? {
        val kParams = method.kotlinFunction?.parameters

        return method.parameters.indices.firstNotNullOfOrNull { index ->
            val param = method.parameters[index]
            val pathVar = param.getAnnotation(PathVariable::class.java) ?: return@firstNotNullOfOrNull null

            val extractedVarName = getAnnotationValue(pathVar.name, pathVar.value, param, kParams, index)

            if (extractedVarName == variableName) {
                args.getOrNull(index)?.toString()
            } else {
                null
            }
        }
    }

    /**
     * Extract a cookie value from method parameters.
     *
     * @param method Controller method to inspect
     * @param args Method arguments
     * @param cookieName Cookie name to search for
     * @return Cookie value as String if found, null otherwise
     */
    fun extractCookieValue(method: Method, args: Array<Any>, cookieName: String): String? {
        val kParams = method.kotlinFunction?.parameters

        return method.parameters.indices.firstNotNullOfOrNull { index ->
            val param = method.parameters[index]
            val cookie = param.getAnnotation(CookieValue::class.java) ?: return@firstNotNullOfOrNull null

            val extractedCookieName = getAnnotationValue(cookie.name, cookie.value, param, kParams, index)

            if (extractedCookieName == cookieName) {
                args.getOrNull(index)?.toString()
            } else {
                null
            }
        }
    }

    /**
     * Extract scope identifier based on scope type strategy.
     *
     * Supports multiple extraction strategies:
     * - CART_TOKEN: Extracts from @RequestParam("token") or @CookieValue("cart_token")
     * - PLATFORM_ID: Extracts from @RequestHeader("X-Platform-ID")
     * - BOOKING_ID: Extracts from @PathVariable("id") or @PathVariable("bookingId")
     * - CUSTOM: Uses custom parameter name
     *
     * @param method Controller method to inspect
     * @param args Method arguments
     * @param scopeType Type of scope identifier to extract
     * @param customParamName Custom parameter name (used when scopeType is CUSTOM)
     * @return Scope identifier if found, null otherwise
     */
    fun extractScopeId(
        method: Method,
        args: Array<Any>,
        scopeType: IdempotencyScopeType,
        customParamName: String? = null
    ): String? {
        return when (scopeType) {
            IdempotencyScopeType.CART_TOKEN -> {
                extractRequestParam(method, args, "token")
                    ?: extractCookieValue(method, args, "cart_token")
            }

            IdempotencyScopeType.PLATFORM_ID -> {
                extractHeaderValue(method, args, "X-Platform-ID")
            }

            IdempotencyScopeType.BOOKING_ID -> {
                extractPathVariable(method, args, "id")
                    ?: extractPathVariable(method, args, "bookingId")
            }

            IdempotencyScopeType.CUSTOM -> {
                customParamName?.let { name ->
                    extractRequestParam(method, args, name)
                        ?: extractPathVariable(method, args, name)
                        ?: extractHeaderValue(method, args, name)
                }
            }

            IdempotencyScopeType.NONE -> null
        }
    }

    /**
     * Get annotation value from name or value attribute, falling back to parameter name.
     */
    private fun getAnnotationValue(
        annotationName: String,
        annotationValue: String,
        param: Parameter,
        kParams: List<kotlin.reflect.KParameter>?,
        index: Int
    ): String {
        return annotationName.takeIf { it.isNotBlank() }
            ?: annotationValue.takeIf { it.isNotBlank() }
            ?: kParams?.getOrNull(index + 1)?.name
            ?: param.name
    }
}

/**
 * Scope type for idempotency key namespacing.
 *
 * Defines standard strategies for extracting scope identifiers
 * from controller method parameters, ensuring consistency across modules.
 */
enum class IdempotencyScopeType {
    /**
     * Cart token scope - extracts from @RequestParam("token") or @CookieValue("cart_token").
     * Used for cart operations where idempotency is scoped to a specific cart.
     */
    CART_TOKEN,

    /**
     * Platform ID scope - extracts from @RequestHeader("X-Platform-ID").
     * Used for platform API operations where idempotency is scoped to a specific platform.
     */
    PLATFORM_ID,

    /**
     * Booking ID scope - extracts from @PathVariable("id") or @PathVariable("bookingId").
     * Used for booking operations where idempotency is scoped to a specific booking.
     */
    BOOKING_ID,

    /**
     * Custom scope - uses custom parameter name for extraction.
     * Fallback for non-standard parameter names or sources.
     */
    CUSTOM,

    /**
     * No scope - idempotency key is not scoped to any specific entity.
     * Used for global operations that don't have a natural scope identifier.
     */
    NONE
}

