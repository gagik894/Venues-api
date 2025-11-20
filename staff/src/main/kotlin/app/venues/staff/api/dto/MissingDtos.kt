package app.venues.staff.api.dto

import app.venues.staff.domain.StaffStatus
import jakarta.validation.constraints.NotBlank
import java.util.*

data class VerifyEmailRequest(
    @field:NotBlank val token: String
)

data class UpdateStaffStatusRequest(
    val staffId: UUID,
    val status: StaffStatus
)
