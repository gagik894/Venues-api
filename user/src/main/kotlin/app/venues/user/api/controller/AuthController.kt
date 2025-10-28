package app.venues.user.api.controller

import app.venues.common.model.ApiResponse
import app.venues.user.api.dto.LoginRequest
import app.venues.user.api.dto.LoginResponse
import app.venues.user.api.dto.UserRegistrationRequest
import app.venues.user.api.dto.UserResponse
import app.venues.user.api.mapper.UserMapper
import app.venues.user.service.UserAuthService
import app.venues.user.service.UserService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for user authentication operations.
 *
 * Endpoints:
 * - POST /api/v1/auth/user/register - Register new user
 * - POST /api/v1/auth/user/login - Authenticate and get JWT token
 *
 * All endpoints are public (no authentication required).
 * Security configuration permits these paths.
 */
@RestController
@RequestMapping("/api/v1/auth/user")
@Tag(name = "User Authentication", description = "User registration and login endpoints")
class AuthController(
    private val userService: UserService,
    private val userAuthService: UserAuthService
) {

    private val logger = KotlinLogging.logger {}

    /**
     * Registers a new user account.
     *
     * Creates a new user with PENDING_VERIFICATION status.
     * User must verify email before full access.
     *
     * @param request Registration details
     * @return Created user information (without password)
     */
    @PostMapping("/register")
    @Operation(
        summary = "Register new user",
        description = "Create a new user account. Email verification required before login."
    )
    fun register(
        @Valid @RequestBody request: UserRegistrationRequest
    ): ResponseEntity<ApiResponse<UserResponse>> {
        logger.info { "Registration request received for email: ${request.email}" }

        val user = userService.registerUser(request)
        val response = UserMapper.toResponse(user)

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(response))
    }

    /**
     * Authenticates a user and returns JWT token.
     *
     * Validates credentials and generates JWT token for authenticated access.
     * Token must be included in Authorization header for protected endpoints.
     *
     * @param request Login credentials
     * @return JWT token and user information
     */
    @PostMapping("/login")
    @Operation(
        summary = "User login",
        description = "Authenticate with email and password. Returns JWT token for API access."
    )
    fun login(
        @Valid @RequestBody request: LoginRequest
    ): ResponseEntity<ApiResponse<LoginResponse>> {
        logger.info { "Login request received for email: ${request.email}" }

        val loginResponse = userAuthService.login(request)

        return ResponseEntity.ok(ApiResponse.success(loginResponse))
    }
}

