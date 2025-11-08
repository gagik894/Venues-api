package app.venues.shared.security.jwt

import app.venues.common.constants.AppConstants
import io.github.oshai.kotlinlogging.KotlinLogging
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service
import java.util.*
import javax.crypto.SecretKey

/**
 * Service for JWT token generation, validation, and parsing.
 *
 * This service is shared across all modules and handles JWT operations for:
 * - Users (with ROLE_USER)
 * - Venues (with ROLE_VENUE)
 * - Any other authenticated principals
 *
 * JWT Structure:
 * - Header: Algorithm and token type
 * - Payload: Claims (principal info, expiration, etc.)
 * - Signature: Verifies token integrity
 *
 * Token Claims:
 * - sub (subject): Principal email/identifier
 * - iat (issued at): Token creation timestamp
 * - exp (expiration): Token expiration timestamp
 * - id: Custom claim for principal ID (user ID or venue ID)
 * - role: Custom claim for principal role (ROLE_USER, ROLE_VENUE, etc.)
 *
 * Security:
 * - Tokens are signed with HMAC-SHA256
 * - Secret key must be at least 256 bits (configured via properties)
 * - Tokens expire after configured duration
 * - Same token validation logic applies to all principal types
 *
 * Usage Examples:
 * - User: generateToken(email = "user@example.com", userId = 123, role = "USER")
 * - Venue: generateToken(email = "venue@example.com", userId = 456, role = "VENUE")
 */
@Service
class JwtService {

    private val logger = KotlinLogging.logger {}

    @Value("\${app.security.jwt.secret}")
    private lateinit var jwtSecret: String

    @Value("\${app.security.jwt.expiration-ms}")
    private var jwtExpirationMs: Long = AppConstants.Security.TOKEN_EXPIRATION_HOURS * 60 * 60 * 1000

    /**
     * Gets the secret key for signing tokens.
     * Lazily initialized from configuration.
     */
    private val secretKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtSecret.toByteArray())
    }

    /**
     * Generates a JWT token for any authenticated principal (user, venue, etc.).
     *
     * This method is generic and works for any principal type that needs a JWT token.
     * The role parameter determines what kind of principal this is.
     *
     * @param email Principal's email/identifier (used as subject)
     * @param id Principal's unique ID (user ID, venue ID, etc.)
     * @param role Principal's role (USER, VENUE, ADMIN, etc.) - determines authorization level
     * @return Generated JWT token
     */
    fun generateToken(email: String, id: Long, role: String): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtExpirationMs)

        val token = Jwts.builder()
            .subject(email)
            .claim("id", id)
            .claim("role", role)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(secretKey)
            .compact()

        logger.debug { "Generated JWT token for principal: $email (role: $role)" }

        return token
    }

    /**
     * Extracts the email from a JWT token.
     *
     * The email is stored in the JWT's "sub" (subject) claim and serves as
     * the unique identifier for any principal (user, venue, etc.).
     *
     * Works for any principal type (user, venue, etc.).
     *
     * @param token JWT token
     * @return Email/identifier from token subject claim
     * @throws io.jsonwebtoken.JwtException if token is invalid or expired
     */
    fun getEmailFromToken(token: String): String {
        return try {
            getClaimsFromToken(token).subject
        } catch (e: io.jsonwebtoken.ExpiredJwtException) {
            throw e // Re-throw so calling code can handle token expiration specifically
        }
    }

    /**
     * Extracts the principal ID from a JWT token.
     *
     * Returns the unique identifier for any principal:
     * - User ID for user tokens
     * - Venue ID for venue tokens
     * - Any other principal ID for other token types
     *
     * @param token JWT token
     * @return Principal ID from custom claim
     */
    fun getIdFromToken(token: String): Long {
        val claims = getClaimsFromToken(token)
        return claims["id"] as? Long
            ?: (claims["id"] as? Int)?.toLong()
            ?: throw IllegalArgumentException("Principal ID not found in token")
    }

    /**
     * Extracts the principal role from a JWT token.
     *
     * Examples: USER, VENUE, ADMIN, etc.
     *
     * @param token JWT token
     * @return Principal role from custom claim
     */
    fun getRoleFromToken(token: String): String {
        return getClaimsFromToken(token)["role"] as String
    }

    /**
     * Validates a JWT token.
     *
     * Checks:
     * - Token signature is valid (not tampered with)
     * - Token is not expired
     * - Email in token matches the provided UserDetails username
     *
     * Note: UserDetails.username contains the email for both users and venues.
     *
     * Works for any principal type (user, venue, etc.).
     *
     * @param token JWT token to validate
     * @param userDetails UserDetails to compare against (implements principal details)
     * @return true if token is valid and email matches
     */
    fun validateToken(token: String, userDetails: UserDetails): Boolean {
        return try {
            val email = getEmailFromToken(token)
            // UserDetails.username contains email for both users and venues
            email == userDetails.username && !isTokenExpired(token)
        } catch (e: io.jsonwebtoken.ExpiredJwtException) {
            logger.warn { "Token has expired: ${e.message}" }
            false
        } catch (e: Exception) {
            logger.warn(e) { "Token validation failed" }
            false
        }
    }

    /**
     * Checks if a token is expired.
     *
     * Handles ExpiredJwtException gracefully - if the token is expired,
     * the JWT library will throw an exception, which we catch and return true.
     *
     * @param token JWT token
     * @return true if token is expired, false otherwise
     */
    fun isTokenExpired(token: String): Boolean {
        return try {
            val expiration = getClaimsFromToken(token).expiration
            expiration.before(Date())
        } catch (e: io.jsonwebtoken.ExpiredJwtException) {
            // Token is expired if parsing fails with ExpiredJwtException
            logger.debug { "Token is expired: ${e.message}" }
            true
        } catch (e: Exception) {
            // For other exceptions, consider token invalid
            logger.warn(e) { "Error checking token expiration" }
            true
        }
    }

    /**
     * Extracts all claims from a JWT token.
     *
     * @param token JWT token
     * @return Claims object containing all token claims
     * @throws io.jsonwebtoken.JwtException if token is invalid
     */
    private fun getClaimsFromToken(token: String): Claims {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }

    /**
     * Gets the token expiration duration in milliseconds.
     *
     * @return Expiration duration in ms
     */
    fun getExpirationMs(): Long = jwtExpirationMs
}

