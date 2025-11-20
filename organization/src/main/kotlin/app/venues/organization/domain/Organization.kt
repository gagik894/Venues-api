package app.venues.organization.domain

import app.venues.shared.persistence.domain.AbstractUuidEntity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * Represents the Legal Entity / Tenant.
 *
 * @property name Internal display name (e.g., "Ministry of Culture").
 * @property slug Unique URL identifier (e.g., "ministry-culture").
 * @property customDomain FUTURE-PROOFING: For "culture.gov.am" hosting.
 * @property brandingConfig FUTURE-PROOFING: JSON blob for Ministry colors/logos across all their venues.
 */
@Entity
@Table(
    name = "organizations",
    indexes = [
        Index(name = "idx_org_slug", columnList = "slug", unique = true),
        Index(name = "idx_org_domain", columnList = "custom_domain", unique = true),
        Index(name = "idx_org_status", columnList = "is_active")
    ]
)
class Organization(

    @Column(name = "name", nullable = false, length = 255)
    var name: String,

    @Column(name = "slug", nullable = false, unique = true, length = 100)
    var slug: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    var type: OrganizationType = OrganizationType.PRIVATE,

    // --- Identity & Legal ---
    @Column(name = "legal_name", length = 255)
    var legalName: String? = null,

    @Column(name = "tax_id", length = 50)
    var taxId: String? = null,

    // --- Contact ---
    @Column(name = "contact_email", length = 255)
    var contactEmail: String? = null,

    @Column(name = "phone_number", length = 20)
    var phoneNumber: String? = null,

    @Column(name = "website_url", length = 500)
    var websiteUrl: String? = null,

    // --- Multi-Tenant / White Label Features ---

    /**
     * Enables the organization to host their own portal (e.g., culture.gov.am).
     * The Venue Service will filter venues by looking up the Organization ID associated with this domain.
     */
    @Column(name = "custom_domain", unique = true, length = 255)
    var customDomain: String? = null,

    /**
     * Organization-wide branding.
     * JSON structure: { "primaryColor": "#FF0000", "logo": "url...", "navLinks": [] }
     * Used to theme the specialized portal.
     */
    @Column(name = "branding_config", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    var brandingConfig: Map<String, Any>? = null,

    // --- Status ---
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

) : AbstractUuidEntity()