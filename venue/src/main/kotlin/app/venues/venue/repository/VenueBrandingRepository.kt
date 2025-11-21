package app.venues.venue.repository

import app.venues.venue.domain.VenueBranding
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface VenueBrandingRepository : JpaRepository<VenueBranding, UUID>
