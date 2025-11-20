package app.venues.staff.api.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// --- REQUESTS ---

data class StaffLoginRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank val password: String
)

data class StaffRegisterRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank @field:Size(min = 8) val password: String,
    @field:NotBlank val firstName: String,
    @field:NotBlank val lastName: String
)

// --- RESPONSES ---

data class StaffAuthResponse(
    val token: String, // JWT
    val expiresIn: Long,
    val profile: StaffProfileDto,
    val context: StaffGlobalContextDto // The "Menu" of where they can work
)