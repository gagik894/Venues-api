package app.venues.venue.domain

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.*

@Entity
@Table(name = "venue_branding")
class VenueBranding(

    @Id
    @Column(name = "venue_id")
    var id: UUID? = null, // Manually assigned via MapsId

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId // Shares PK with Venue
    @JoinColumn(name = "venue_id")
    var venue: Venue,

    // --- Colors ---
    @Column(name = "primary_color", length = 7)
    var primaryColor: String? = null,

    @Column(name = "secondary_color", length = 7)
    var secondaryColor: String? = null,

    @Column(name = "favicon_url", length = 500)
    var faviconUrl: String? = null,

    // --- Layout Config (Type-Safe JSON) ---

    @Column(name = "home_hero", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    var homeHero: HeroConfig? = null,

    @Column(name = "about_blocks", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    var aboutBlocks: List<ContentBlock>? = null,

    @Column(name = "contact_config", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    var contactConfig: ContactConfig? = null

)

data class HeroConfig(
    val title: Map<String, String>, // Multilingual
    val subtitle: Map<String, String>?,
    val ctaText: Map<String, String>?,
    val ctaLink: String?
) : java.io.Serializable

data class ContentBlock(
    val type: String, // "text", "image"
    val content: Map<String, String>?,
    val imageUrl: String?
) : java.io.Serializable

data class ContactConfig(
    val mapUrl: String?,
    val showForm: Boolean = true
) : java.io.Serializable