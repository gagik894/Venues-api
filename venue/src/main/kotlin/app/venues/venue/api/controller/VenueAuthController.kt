package app.venues.venue.api.controller

import app.venues.venue.service.VenueAuthService
import app.venues.venue.service.VenueService
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST Controller for venue authentication operations.
 *
 * Endpoints:
 * - POST /api/v1/venues/auth/register - Register new venue
 * - POST /api/v1/venues/auth/login - Venue login
 *
 * All endpoints return standardized ApiResponse wrapper.
 *
 * Design Pattern:
 * - JWT token generation is handled by VenueAuthService.login()
 * - Controller only handles HTTP request/response routing
 * - This matches the UserAuthService pattern for consistency
 */
@RestController
@RequestMapping("/api/v1/venues/auth")
@Validated
@Tag(name = "Venue Authentication", description = "Venue registration and authentication endpoints")
class VenueAuthController(
    private val venueService: VenueService,
    private val venueAuthService: VenueAuthService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    //TODO: MOVE TO STAFF AUTH CONTROLLER
//
//    /**
//     * Register a new venue account.
//     *
//     * Creates a new venue with PENDING_APPROVAL status.
//     * Admin approval is required before the venue can post events.
//     *
//     * @param request Registration request with venue details
//     * @return Created venue information
//     */
//    @PostMapping("/register")
//    @ResponseStatus(HttpStatus.CREATED)
//    @Operation(
//        summary = "Register new venue",
//        description = "Create a new venue account. Status will be PENDING_APPROVAL until admin approves."
//    )
//    fun register(
//        @Valid @RequestBody request: VenueRegistrationRequest
//    ): ApiResponse<VenueResponse> {
//        logger.info("Venue registration request for email: {}", request.email)
//
//        val venue = venueService.registerVenue(request)
//
//        return ApiResponse.success(
//            data = venue,
//            message = "Venue registered successfully. Your account is pending admin approval."
//        )
//    }
//
//    /**
//     * Authenticate venue and return JWT token.
//     *
//     * Validates credentials and returns access token.
//     * Token must be included in Authorization header for protected endpoints.
//     *
//     * @param request Login credentials
//     * @return JWT token and venue information
//     */
//    @PostMapping("/login")
//    @Operation(
//        summary = "Venue login",
//        description = "Authenticate venue and receive JWT token"
//    )
//    fun login(
//        @Valid @RequestBody request: VenueLoginRequest
//    ): ApiResponse<VenueLoginResponse> {
//        logger.info("Venue login request for email: {}", request.email)
//
//        // Service handles authentication and token generation
//        val loginResponse = venueAuthService.login(request.email, request.password)
//
//        return ApiResponse.success(
//            data = loginResponse,
//            message = "Login successful"
//        )
//    }
}

