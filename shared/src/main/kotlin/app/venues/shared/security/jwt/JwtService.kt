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
    fun generateToken(email: String, id: UUID, role: String): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtExpirationMs)

        val token = Jwts.builder()
            .subject(email)
            // Store UUID as a string, which is standard for JWT claims
            .claim("id", id.toString())
            .claim("role", role)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(secretKey)
            .compact()

        logger.debug { "Generated JWT token for principal: $email (role: $role)" }

        return token
    }

    /**
     * Extracts all claims from a JWT token.
     * This is the primary method for parsing and validating the token.
     *
     * @param token JWT token
     * @return Claims object containing all token claims
     * @throws io.jsonwebtoken.JwtException if token is invalid or expired
     */
    fun getAllClaimsFromToken(token: String): Claims {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }

    /**
     * Extracts the email (subject) from a pre-parsed claims set.
     * @param claims The claims set from [getAllClaimsFromToken]
     * @return Email/identifier from token subject claim
     */
    fun getEmailFromClaims(claims: Claims): String = claims.subject

    /**
     * Extracts the email from a JWT token (parses token).
     * @param token JWT token
     * @return Email/identifier from token subject claim
     */
    fun getEmailFromToken(token: String): String {
        return getAllClaimsFromToken(token).subject
    }

    /**
     * Extracts the principal ID from a pre-parsed claims set.
     *
     * @param claims The claims set from [getAllClaimsFromToken]
     * @return Principal ID (UUID)
     * @throws IllegalArgumentException if 'id' claim is missing, not a string, or not a valid UUID
     */
    fun getIdFromClaims(claims: Claims): UUID {
        val idString = claims["id"] as? String
            ?: throw IllegalArgumentException("Principal ID 'id' claim not found or not a String in token")
        return try {
            UUID.fromString(idString)
        } catch (e: IllegalArgumentException) {
            // Log the problematic string for debugging
            logger.warn(e) { "Principal ID 'id' claim in token is not a valid UUID: $idString" }
            throw IllegalArgumentException("Principal ID 'id' claim is not a valid UUID string", e)
        }
    }

    /**
     * Extracts the principal ID from a JWT token (parses token).
     * @param token JWT token
     * @return Principal ID from custom claim
     */
    fun getIdFromToken(token: String): UUID {
        return getIdFromClaims(getAllClaimsFromToken(token))
    }

    /**
     * Extracts the principal role from a pre-parsed claims set.
     *
     * @param claims The claims set from [getAllClaimsFromToken]
     * @return Principal role string
     * @throws IllegalArgumentException if 'role' claim is missing or not a string
     */
    fun getRoleFromClaims(claims: Claims): String {
        return claims["role"] as? String
            ?: throw IllegalArgumentException("Principal 'role' claim not found or not a String in token")
    }

    /**
     * Extracts the principal role from a JWT token (parses token).
     * @param token JWT token
     * @return Principal role from custom claim
     */
    fun getRoleFromToken(token: String): String {
        return getRoleFromClaims(getAllClaimsFromToken(token))
    }

    /**
     * Checks if a token is expired from a pre-parsed claims set.
     * @param claims The claims set from [getAllClaimsFromToken]
     * @return true if token is expired, false otherwise
     */
    fun isTokenExpired(claims: Claims): Boolean {
        return claims.expiration.before(Date())
    }

    /**
     * Checks if a token is expired (parses token).
     * @param token JWT token
     * @return true if token is expired, false otherwise
     */
    fun isTokenExpired(token: String): Boolean {
        return try {
            val expiration = getAllClaimsFromToken(token).expiration
            expiration.before(Date())
        } catch (e: io.jsonwebtoken.ExpiredJwtException) {
            logger.debug { "Token is expired: ${e.message}" }
            true
        } catch (e: Exception) {
            logger.warn(e) { "Error checking token expiration" }
            true
        }
    }

    /**
     * Validates a JWT token against UserDetails.
     *
     * @param token JWT token to validate
     * @param userDetails UserDetails to compare against (implements principal details)
     * @return true if token is valid and email matches
     */
    fun validateToken(token: String, userDetails: UserDetails): Boolean {
        return try {
            val claims = getAllClaimsFromToken(token)
            val email = getEmailFromClaims(claims)
            // UserDetails.username contains email for both users and venues
            email == userDetails.username && !isTokenExpired(claims)
        } catch (e: io.jsonwebtoken.ExpiredJwtException) {
            logger.warn { "Token has expired: ${e.message}" }
            false
        } catch (e: Exception) {
            logger.warn(e) { "Token validation failed" }
            false
        }
    }

    /**
     * Gets the token expiration duration in milliseconds.
     *
     * @return Expiration duration in ms
     */
    fun getExpirationMs(): Long = jwtExpirationMs
}