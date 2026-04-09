package app.venues.location.service

import app.venues.location.api.dto.*
import app.venues.location.domain.City
import app.venues.location.domain.Region

fun Region.toResponse(lang: String = "en"): RegionResponse {
    return RegionResponse(
        code = this.code,
        names = this.names,
        name = this.getName(lang),
        displayOrder = this.displayOrder,
        isActive = this.isActive
    )
}

fun City.toResponse(lang: String = "en"): CityResponse {
    return CityResponse(
        slug = this.slug,
        names = this.names,
        name = this.getName(lang),
        region = RegionCompact(
            code = this.region.code,
            name = this.region.getName(lang)
        ),
        officialId = this.officialId,
        displayOrder = this.displayOrder,
        isActive = this.isActive
    )
}

fun City.toCompact(lang: String = "en"): CityCompact {
    return CityCompact(
        slug = this.slug,
        name = this.getName(lang),
        regionName = this.region.getName(lang)
    )
}
