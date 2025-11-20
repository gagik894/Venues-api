package app.venues.staff.api.controller

import app.venues.common.model.ApiResponse
import app.venues.staff.api.dto.StaffAuthResponse
import app.venues.staff.api.dto.StaffLoginRequest
import app.venues.staff.api.dto.StaffRegisterRequest
import app.venues.staff.api.dto.VerifyEmailRequest
import app.venues.staff.service.StaffAuthService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/staff/auth")
@Tag(name = "Staff Auth", description = "Authentication and Registration for Staff/Admins")
class StaffAuthController(
    private val authService: StaffAuthService
) {
    private val logger = KotlinLogging.logger {}

    @PostMapping("/register")
    @Operation(summary = "Register new staff", description = "Creates a new Staff Identity (Global Account)")
    fun register(@Valid @RequestBody req: StaffRegisterRequest): ApiResponse<StaffAuthResponse> {
        logger.info { "Registering new staff email: ${req.email}" }

        val response = authService.register(req)

        return ApiResponse.success(
            data = response,
            message = "Registration successful. Please verify your email."
        )
    }

    @PostMapping("/login")
    @Operation(summary = "Staff Login", description = "Returns JWT and full Context (Orgs/Venues)")
    fun login(@Valid @RequestBody req: StaffLoginRequest): ApiResponse<StaffAuthResponse> {
        logger.debug { "Login attempt for: ${req.email}" }

        val response = authService.login(req)

        return ApiResponse.success(
            data = response,
            message = "Login successful"
        )
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify Email", description = "Activates account using token from email")
    fun verifyEmail(@Valid @RequestBody req: VerifyEmailRequest): ApiResponse<Unit> {
        logger.info { "Verifying email with token" }

        authService.verifyEmail(req)

        return ApiResponse.success(
            message = "Email verified successfully"
        )
    }
}