package app.venues.payment.repository

import app.venues.payment.domain.Payment
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository for Payment entity operations.
 *
 * Provides standard CRUD operations for managing payment records.
 */
@Repository
interface PaymentRepository : JpaRepository<Payment, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    fun findByIdWithLock(id: UUID): Optional<Payment>
}
