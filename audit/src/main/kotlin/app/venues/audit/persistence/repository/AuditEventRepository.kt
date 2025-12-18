package app.venues.audit.persistence.repository

import app.venues.audit.persistence.domain.AuditEventEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface AuditEventRepository : JpaRepository<AuditEventEntity, UUID>
