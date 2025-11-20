package app.venues.organization.domain

enum class OrganizationType {
    GOVERNMENT, // Ministries, State Bodies
    MUNICIPAL,  // City Halls
    CULTURAL,   // Museums, Theaters (State Owned)
    PRIVATE,    // Event Organizers, Clubs
    NON_PROFIT  // NGOs
}