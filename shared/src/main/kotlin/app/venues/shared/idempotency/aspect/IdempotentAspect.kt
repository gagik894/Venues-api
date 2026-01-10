package app.venues.shared.idempotency.aspect

import app.venues.shared.idempotency.IdempotencyContext
import app.venues.shared.idempotency.IdempotencyKeyExtractor
import app.venues.shared.idempotency.IdempotencyScopeType
import app.venues.shared.idempotency.IdempotencyService
import app.venues.shared.idempotency.annotation.Idempotent
import io.github.oshai.kotlinlogging.KotlinLogging
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.core.annotation.Order
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import java.lang.reflect.Method

/**
 * Enterprise-grade aspect for automatic idempotency protection.
 *
 * **Responsibilities:**
 * 1. Intercept controller methods annotated with @Idempotent
 * 2. Extract Idempotency-Key from request headers
 * 3. Extract scope identifier based on annotation configuration
 * 4. Delegate to IdempotencyService for cache/execution logic
 * 5. Handle errors gracefully (fail-open for availability)
 *
 * **Execution Order:**
 * - @Order(1): Runs BEFORE @Auditable aspect (@Order(2))
 * - Ensures duplicate requests don't create duplicate audit entries
 * - Ensures idempotency is the outermost concern
 *
 * **Behavior:**
 * - If Idempotency-Key missing: proceeds without protection
 * - If Redis unavailable: proceeds without protection (fail-open)
 * - If operation fails: releases lock, doesn't cache error
 * - If operation succeeds: caches result for 24 hours
 *
 * **Type Safety:**
 * - Automatically extracts generic types from ResponseEntity<T>
 * - Falls back to method return type for non-ResponseEntity responses
 * - Uses strongly-typed IdempotencyContext for compile-time safety
 *
 * @see Idempotent
 * @see IdempotencyService
 * @see IdempotencyKeyExtractor
 */
@Aspect
@Component
@Order(1)
class IdempotentAspect(
    private val idempotencyService: IdempotencyService
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Intercept methods annotated with @Idempotent.
     *
     * @param joinPoint Method invocation join point
     * @param idempotent Annotation configuration
     * @return Result of method execution (cached or freshly computed)
     */
    @Around("@annotation(idempotent)")
    fun applyIdempotency(joinPoint: ProceedingJoinPoint, idempotent: Idempotent): Any? {
        val method = extractMethod(joinPoint) ?: return joinPoint.proceed()
        val args = joinPoint.args

        // Extract idempotency key from request header
        val idempotencyKey = IdempotencyKeyExtractor.extractIdempotencyKey(method, args)

        // If no idempotency key provided, proceed without protection
        if (idempotencyKey.isNullOrBlank()) {
            logger.trace { "No idempotency key provided for ${method.name}, proceeding without protection" }
            return joinPoint.proceed()
        }

        // Extract scope identifier based on annotation configuration
        val scopeId = extractScopeId(method, args, idempotent)

        // Determine response type from method signature
        val responseType = extractResponseType(method)

        // Build idempotency context
        val context = IdempotencyContext(
            keyPrefix = idempotent.keyPrefix,
            operation = idempotent.endpoint,
            scopeId = scopeId,
            idempotencyKey = idempotencyKey,
            responseType = responseType
        )

        logger.debug { "Applying idempotency: ${context.getDescription()}" }

        // Delegate to idempotency service
        return idempotencyService.executeWithIdempotency(context) {
            joinPoint.proceed()
        }
    }

    /**
     * Extract Method from join point signature.
     */
    private fun extractMethod(joinPoint: ProceedingJoinPoint): Method? {
        val signature = joinPoint.signature as? MethodSignature
        if (signature == null) {
            logger.warn { "Cannot extract method signature from join point: ${joinPoint.signature}" }
            return null
        }
        return signature.method
    }

    /**
     * Extract scope identifier based on annotation configuration.
     *
     * Uses IdempotencyKeyExtractor for consistent, tested extraction logic.
     *
     * @param method Controller method
     * @param args Method arguments
     * @param idempotent Annotation configuration
     * @return Scope identifier if found, null otherwise
     */
    private fun extractScopeId(method: Method, args: Array<Any>, idempotent: Idempotent): String? {
        return when (idempotent.scopeType) {
            IdempotencyScopeType.CUSTOM -> {
                val customName = idempotent.customScopeName.takeIf { it.isNotBlank() }
                if (customName == null) {
                    logger.warn { "CUSTOM scope type specified but customScopeName is blank for ${method.name}" }
                    null
                } else {
                    IdempotencyKeyExtractor.extractScopeId(method, args, IdempotencyScopeType.CUSTOM, customName)
                }
            }

            else -> {
                IdempotencyKeyExtractor.extractScopeId(method, args, idempotent.scopeType)
            }
        }
    }

    /**
     * Extract response type from method signature.
     *
     * For ResponseEntity<T>, extracts T.
     * For other types, returns the return type directly.
     *
     * @param method Controller method
     * @return Response type class
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractResponseType(method: Method): Class<Any> {
        val returnType = method.returnType

        // Handle ResponseEntity<T> - extract generic type
        if (ResponseEntity::class.java.isAssignableFrom(returnType)) {
            val genericType = extractGenericTypeFromResponseEntity(method)
            if (genericType != null) {
                return genericType as Class<Any>
            }
        }

        // Return direct type
        return returnType as Class<Any>
    }

    /**
     * Extract generic type T from ResponseEntity<T>.
     *
     * @param method Controller method
     * @return Generic type class if extractable, null otherwise
     */
    private fun extractGenericTypeFromResponseEntity(method: Method): Class<*>? {
        return try {
            val genericReturnType = method.genericReturnType
            if (genericReturnType is java.lang.reflect.ParameterizedType) {
                val actualType = genericReturnType.actualTypeArguments.firstOrNull()
                if (actualType is Class<*>) {
                    actualType
                } else {
                    logger.warn { "Cannot extract generic type from ResponseEntity for ${method.name}" }
                    null
                }
            } else {
                logger.warn { "Method return type is not parameterized: ${method.name}" }
                null
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to extract generic type from method ${method.name}" }
            null
        }
    }
}


