package app.venues.location.api.dto

import app.venues.location.domain.City
import app.venues.location.domain.Region
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LocationDtosTest {

    @Test
    fun `region response uses requested language with fallback to english`() {
        val region = Region(
            code = "AM-ER",
            names = mapOf("en" to "Yerevan", "hy" to "Երևան")
        )

        val localized = RegionResponse.from(region, "hy")
        val fallback = RegionResponse.from(region, "ru")

        assertEquals("Երևան", localized.name)
        assertEquals("Yerevan", fallback.name)
    }

    @Test
    fun `city response localizes city and region and falls back to slug`() {
        val region = Region(
            code = "AM-SH",
            names = mapOf("en" to "Shirak", "hy" to "Շիրակ")
        )
        val city = City(
            region = region,
            slug = "gyumri",
            names = mapOf("en" to "Gyumri", "hy" to "Գյումրի")
        )

        val localized = CityResponse.from(city, "hy")
        assertEquals("Գյումրի", localized.name)
        assertEquals("Շիրակ", localized.region.name)

        val slugFallbackCity = City(
            region = region,
            slug = "nameless-city",
            names = emptyMap()
        )
        val slugFallbackResponse = CityResponse.from(slugFallbackCity, "ru")
        assertEquals("nameless-city", slugFallbackResponse.name)
    }
}
