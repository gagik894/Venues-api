package app.venues.staff.api.controller

import app.venues.audit.annotation.AuditMetadata
import app.venues.audit.annotation.Auditable
import app.venues.common.model.ApiResponse
import app.venues.staff.api.dto.*
import app.venues.staff.service.StaffAuthService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
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

    @Value("\${app.security.cookie.secure}")
    private var cookieSecure: Boolean = true

    @PostMapping("/register")
    @Operation(summary = "Register new staff", description = "Creates a new Staff Identity (Global Account)")
    @Auditable(action = "STAFF_REGISTER", subjectType = "staff", includeVenueId = false)
    fun register(
        @AuditMetadata("request") @Valid @RequestBody req: StaffRegisterRequest,
        response: HttpServletResponse
    ): ApiResponse<StaffAuthResponse> {
        logger.info { "Registering new staff email: ${req.email}" }

        val authResponse = authService.register(req)

        setAuthCookie(response, authResponse)

        return ApiResponse.success(
            data = authResponse,
            message = "Registration successful. Please verify your email."
        )
    }

    @PostMapping("/login")
    @Operation(summary = "Staff Login", description = "Returns JWT (in HttpOnly cookie) and full Context (Orgs/Venues)")
    @Auditable(action = "STAFF_LOGIN", subjectType = "staff", includeVenueId = false)
    fun login(
        @AuditMetadata("request") @Valid @RequestBody req: StaffLoginRequest,
        response: HttpServletResponse
    ): ApiResponse<StaffAuthResponse> {
        logger.debug { "Login attempt for: ${req.email}" }

        val authResponse = authService.login(req)

        setAuthCookie(response, authResponse)

        return ApiResponse.success(
            data = authResponse,
            message = "Login successful"
        )
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify Email", description = "Activates account using token from email")
    @Auditable(action = "STAFF_VERIFY_EMAIL", subjectType = "staff_verification", includeVenueId = false)
    fun verifyEmail(@Valid @RequestBody req: VerifyEmailRequest): ApiResponse<Unit> {
        logger.info { "Verifying email with token" }

        authService.verifyEmail(req)

        return ApiResponse.success(
            message = "Email verified successfully"
        )
    }

    @PostMapping("/accept-invite")
    @Operation(summary = "Accept staff invite", description = "Sets password and activates invited staff account")
    @Auditable(action = "STAFF_ACCEPT_INVITE", subjectType = "staff", includeVenueId = false)
    fun acceptInvite(
        @AuditMetadata("request") @Valid @RequestBody req: AcceptInviteRequest,
        response: HttpServletResponse
    ): ApiResponse<StaffAuthResponse> {
        val authResponse = authService.acceptInvite(req)

        setAuthCookie(response, authResponse)

        return ApiResponse.success(
            data = authResponse,
            message = "Invite accepted successfully"
        )
    }

    private fun setAuthCookie(response: HttpServletResponse, authResponse: StaffAuthResponse) {
        val cookie = Cookie("staff_auth_token", authResponse.token)
        cookie.isHttpOnly = true
        cookie.secure = cookieSecure
        cookie.path = "/"
        cookie.maxAge = authResponse.expiresIn.toInt()
        cookie.setAttribute("SameSite", "Strict")
        response.addCookie(cookie)
    }
}