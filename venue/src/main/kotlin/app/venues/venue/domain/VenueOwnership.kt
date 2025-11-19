package app.venues.venue.domain

enum class VenueOwnership {
    STATE_OWNED,   // SNCO / POAK
    MUNICIPAL,     // City owned / Hamaynk
    PRIVATE,       // LLC / CJSC
    NGO,           // Non-profit
    OTHER
}