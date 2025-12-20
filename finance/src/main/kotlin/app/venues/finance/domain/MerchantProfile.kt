package app.venues.finance.domain

import app.venues.finance.api.dto.PaymentConfig
import app.venues.finance.converter.PaymentConfigConverter
import app.venues.shared.persistence.domain.AbstractUuidEntity
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.*

/**
 * Represents a financial destination (Merchant Profile).
 * Decouples financial ownership from administrative ownership.
 *
 * A Merchant Profile contains:
 * - Legal entity details (Name, Tax ID)
 * - Payment gateway configuration (Stripe keys, Bank details, etc.)
 *
 * It is linked to an Organization (Owner), but can be assigned to specific Venues or Events.
 */
@Entity
@Table(name = "merchant_profile")
class MerchantProfile(

    /**
     * Display name of the merchant profile (e.g., "Main Corp", "Event Specific LLC").
     */
    @Column(nullable = false)
    var name: String,

    /**
     * Legal name of the business entity.
     */
    @Column(name = "legal_name")
    var legalName: String? = null,

    /**
     * Tax Identification Number.
     */
    @Column(name = "tax_id")
    var taxId: String? = null,

    /**
     * ID of the Organization that owns this profile.
     * This is a weak reference (UUID) to the Organization module.
     */
    @Column(name = "organization_id", nullable = false)
    var organizationId: UUID,

    /**
     * Encrypted payment configuration.
     * Stores credentials for payment gateways (Stripe, Idram, etc.).
     *
     * @see PaymentConfig
     * @see PaymentConfigConverter
     */
    @Column(name = "config_json", columnDefinition = "TEXT")
    @Convert(converter = PaymentConfigConverter::class)
    var config: PaymentConfig? = null

) : AbstractUuidEntity() {

    /**
     * Check if this profile has a valid payment configuration.
     */
    fun hasPaymentConfig(): Boolean = config != null && config!!.hasAnyProvider()
}
