package app.venues.payment.repository

import app.venues.payment.domain.Payment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository for Payment entity operations.
 *
 * Provides standard CRUD operations for managing payment records.
 */
@Repository
interface PaymentRepository : JpaRepository<Payment, UUID>
