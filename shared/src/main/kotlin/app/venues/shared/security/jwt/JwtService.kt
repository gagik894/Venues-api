/*
 * Venues API - Government-Sponsored Cultural Venues Portal
 * Copyright (c) 2025 Government Cultural Department
 *
 * JWT (JSON Web Token) service for token generation and validation.
 */

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
 * JWT Structure:
 * - Header: Algorithm and token type
 * - Payload: Claims (user info, expiration, etc.)
 * - Signature: Verifies token integrity
 *
 * Token Claims:
 * - sub (subject): User email
 * - iat (issued at): Token creation timestamp
 * - exp (expiration): Token expiration timestamp
 * - userId: Custom claim for user ID
 * - role: Custom claim for user role
 *
 * Security:
 * - Tokens are signed with HMAC-SHA256
 * - Secret key must be at least 256 bits
 * - Tokens expire after configured duration
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
     * Generates a JWT token for a user.
     *
     * @param email User's email (used as subject)
     * @param userId User's ID
     * @param role User's role
     * @return Generated JWT token
     */
    fun generateToken(email: String, userId: Long, role: String): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtExpirationMs)

        val token = Jwts.builder()
            .subject(email)
            .claim("userId", userId)
            .claim("role", role)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(secretKey)
            .compact()

        logger.debug { "Generated JWT token for user: $email" }

        return token
    }

    /**
     * Extracts the username (email) from a JWT token.
     *
     * @param token JWT token
     * @return Username (email) from token subject
     */
    fun getUsernameFromToken(token: String): String {
        return getClaimsFromToken(token).subject
    }

    /**
     * Extracts the user ID from a JWT token.
     *
     * @param token JWT token
     * @return User ID from custom claim
     */
    fun getUserIdFromToken(token: String): Long {
        val claims = getClaimsFromToken(token)
        return claims["userId"] as? Long
            ?: (claims["userId"] as? Int)?.toLong()
            ?: throw IllegalArgumentException("User ID not found in token")
    }

    /**
     * Extracts the user role from a JWT token.
     *
     * @param token JWT token
     * @return User role from custom claim
     */
    fun getRoleFromToken(token: String): String {
        return getClaimsFromToken(token)["role"] as String
    }

    /**
     * Validates a JWT token.
     *
     * Checks:
     * - Token signature is valid
     * - Token is not expired
     * - Username matches the provided UserDetails
     *
     * @param token JWT token to validate
     * @param userDetails UserDetails to compare against
     * @return true if token is valid
     */
    fun validateToken(token: String, userDetails: UserDetails): Boolean {
        return try {
            val username = getUsernameFromToken(token)
            username == userDetails.username && !isTokenExpired(token)
        } catch (e: Exception) {
            logger.warn(e) { "Token validation failed" }
            false
        }
    }

    /**
     * Checks if a token is expired.
     *
     * @param token JWT token
     * @return true if token is expired
     */
    fun isTokenExpired(token: String): Boolean {
        val expiration = getClaimsFromToken(token).expiration
        return expiration.before(Date())
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

