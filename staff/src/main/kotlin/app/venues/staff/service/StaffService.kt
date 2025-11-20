package app.venues.staff.service

import app.venues.staff.api.StaffApi
import org.springframework.stereotype.Service
import java.util.*

@Service
class StaffService : StaffApi {

    override fun hasPermission(userId: UUID, permission: String): Boolean {
        // TODO: Implement actual permission logic
        return true
    }
}
