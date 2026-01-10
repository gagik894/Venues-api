package app.venues.shared.idempotency.aspect

import app.venues.shared.idempotency.IdempotencyService
import app.venues.shared.idempotency.annotation.Idempotent
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import java.lang.reflect.Method
import java.util.*
import kotlin.reflect.jvm.kotlinFunction

/**
 * Aspect that automatically applies idempotency protection to methods annotated with @Idempotent.
 *
 * Responsibilities:
 * 1. Intercept controller methods annotated with @Idempotent
 * 2. Extract Idempotency-Key from @RequestHeader("Idempotency-Key")
 * 3. Extract namespace value from method parameters (cartToken, platformId, etc.)
 * 4. Apply idempotency protection using IdempotencyService
 * 5. Return cached result if available, otherwise execute and cache
 *
 * Execution Order:
 * - Runs BEFORE @Auditable aspect to ensure idempotency is checked first
 * - If idempotency key is missing, proceeds normally without protection
 */
@Aspect
@Component
@Order(1) // Execute before @Auditable aspect (which is typically @Order(2))
class IdempotentAspect(
    private val idempotencyService: IdempotencyService,
    private val objectMapper: ObjectMapper
) {
    private val logger = KotlinLogging.logger {}

    @Around("@annotation(idempotent)")
    fun applyIdempotency(joinPoint: ProceedingJoinPoint, idempotent: Idempotent): Any? {
        val method = (joinPoint.signature as? org.aspectj.lang.reflect.MethodSignature)?.method
            ?: return joinPoint.proceed()

        val args = joinPoint.args

        // Extract idempotency key from request header
        val idempotencyKey = extractIdempotencyKey(method, args)

        // If no idempotency key provided, proceed normally
        if (idempotencyKey.isNullOrBlank()) {
            return joinPoint.proceed()
        }

        // Extract namespace value (cart token, platform ID, etc.)
        val namespaceValue = extractNamespaceValue(method, args, idempotent.namespaceKey)

        // Build namespace: endpoint + namespace value if available
        val namespace = if (namespaceValue != null) {
            "${idempotent.endpoint}:$namespaceValue"
        } else {
            idempotent.endpoint
        }

        // Determine response type from method return type
        val returnType = method.returnType
        val responseType = if (returnType.isAssignableFrom(org.springframework.http.ResponseEntity::class.java)) {
            // For ResponseEntity, extract the generic type
            extractGenericType(method) ?: returnType
        } else {
            returnType
        }

        logger.debug {
            "Applying idempotency: endpoint=${idempotent.endpoint} " +
                    "namespace=$namespace key=$idempotencyKey"
        }

        // Apply idempotency protection
        return idempotencyService.withIdempotency(
            idempotencyKey = idempotencyKey,
            keyPrefix = idempotent.keyPrefix,
            namespace = namespace,
            responseType = responseType,
            supplier = {
                joinPoint.proceed()
            }
        )
    }

    private fun extractIdempotencyKey(method: Method, args: Array<Any>): String? {
        val kParams = method.kotlinFunction?.parameters
        return method.parameters.indices.firstNotNullOfOrNull { index ->
            val param = method.parameters[index]
            val header = param.getAnnotation(RequestHeader::class.java)
            if (header != null) {
                val headerName = header.name.takeIf { it.isNotBlank() }
                    ?: header.value.takeIf { it.isNotBlank() }
                    ?: kParams?.getOrNull(index + 1)?.name
                    ?: param.name

                if (headerName.equals("Idempotency-Key", ignoreCase = true)) {
                    args.getOrNull(index) as? String
                } else null
            } else null
        }
    }

    private fun extractNamespaceValue(method: Method, args: Array<Any>, namespaceKey: String?): String? {
        if (namespaceKey == null) return null

        val kParams = method.kotlinFunction?.parameters
        
        // Special handling for cartToken: check both token param and cookie
        if (namespaceKey == "cartToken") {
            // First try @RequestParam("token")
            val tokenParam = method.parameters.indices.firstNotNullOfOrNull { index ->
                val param = method.parameters[index]
                val requestParam = param.getAnnotation(RequestParam::class.java)
                if (requestParam != null) {
                    val paramName = requestParam.name.takeIf { it.isNotBlank() }
                        ?: requestParam.value.takeIf { it.isNotBlank() }
                        ?: kParams?.getOrNull(index + 1)?.name
                        ?: param.name
                    if (paramName == "token") {
                        args.getOrNull(index) as? UUID
                    } else null
                } else null
            }
            if (tokenParam != null) return tokenParam.toString()
            
            // Then try @CookieValue("cart_token")
            val cookieToken = method.parameters.indices.firstNotNullOfOrNull { index ->
                val param = method.parameters[index]
                val cookieValue = param.getAnnotation(CookieValue::class.java)
                if (cookieValue != null) {
                    val cookieName = cookieValue.name.takeIf { it.isNotBlank() }
                        ?: cookieValue.value.takeIf { it.isNotBlank() }
                    if (cookieName == "cart_token") {
                        args.getOrNull(index) as? UUID
                    } else null
                } else null
            }
            if (cookieToken != null) return cookieToken.toString()
        }
        
        // Special handling for bookingId: check @PathVariable
        if (namespaceKey == "bookingId") {
            val bookingId = method.parameters.indices.firstNotNullOfOrNull { index ->
                val param = method.parameters[index]
                val pathVar = param.getAnnotation(PathVariable::class.java)
                if (pathVar != null) {
                    val varName = pathVar.name.takeIf { it.isNotBlank() }
                        ?: pathVar.value.takeIf { it.isNotBlank() }
                        ?: kParams?.getOrNull(index + 1)?.name
                        ?: param.name
                    if (varName == "id" || varName == "bookingId") {
                        args.getOrNull(index) as? UUID
                    } else null
                } else null
            }
            if (bookingId != null) return bookingId.toString()
        }
        
        // Generic extraction for other namespace keys
        return method.parameters.indices.firstNotNullOfOrNull { index ->
            val param = method.parameters[index]
            val arg = args.getOrNull(index)

            // Check @RequestParam
            val requestParam = param.getAnnotation(RequestParam::class.java)
            if (requestParam != null) {
                val paramName = requestParam.name.takeIf { it.isNotBlank() }
                    ?: requestParam.value.takeIf { it.isNotBlank() }
                    ?: kParams?.getOrNull(index + 1)?.name
                    ?: param.name

                if (paramName == namespaceKey) {
                    return@firstNotNullOfOrNull arg?.toString()
                }
            }

            // Check @PathVariable
            val pathVar = param.getAnnotation(PathVariable::class.java)
            if (pathVar != null) {
                val varName = pathVar.name.takeIf { it.isNotBlank() }
                    ?: pathVar.value.takeIf { it.isNotBlank() }
                    ?: kParams?.getOrNull(index + 1)?.name
                    ?: param.name
                if (varName == namespaceKey) {
                    return@firstNotNullOfOrNull arg?.toString()
                }
            }

            // Check @RequestHeader (for platformId)
            val requestHeader = param.getAnnotation(RequestHeader::class.java)
            if (requestHeader != null) {
                val headerName = requestHeader.name.takeIf { it.isNotBlank() }
                    ?: requestHeader.value.takeIf { it.isNotBlank() }
                    ?: kParams?.getOrNull(index + 1)?.name

                if ((headerName == "X-Platform-ID" && namespaceKey == "platformId") ||
                    (param.name == namespaceKey)
                ) {
                    return@firstNotNullOfOrNull arg?.toString()
                }
            }

            // Check parameter name match
            val paramName = kParams?.getOrNull(index + 1)?.name ?: param.name
            if (paramName == namespaceKey) {
                return@firstNotNullOfOrNull arg?.toString()
            }

            null
        }
    }

    private fun extractGenericType(method: Method): Class<*>? {
        return try {
            val returnType = method.genericReturnType
            if (returnType is java.lang.reflect.ParameterizedType) {
                val actualType = returnType.actualTypeArguments.firstOrNull()
                if (actualType is Class<*>) {
                    actualType
                } else null
            } else null
        } catch (e: Exception) {
            logger.warn(e) { "Failed to extract generic type from method ${method.name}" }
            null
        }
    }
}
