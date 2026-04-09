package app.venues.location.api.dto

import app.venues.location.domain.City
import app.venues.location.domain.Region
import app.venues.location.service.toResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LocationDtosTest {

    @Test
    fun `region response uses requested language with fallback to english`() {
        val region = Region(
            code = "AM-ER",
            names = mapOf("en" to "Yerevan", "hy" to "Երևան")
        )

        val localized = region.toResponse("hy")
        val fallback = region.toResponse("ru")

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

        val localized = city.toResponse("hy")
        assertEquals("Գյումրի", localized.name)
        assertEquals("Շիրակ", localized.region.name)

        val slugFallbackCity = City(
            region = region,
            slug = "nameless-city",
            names = emptyMap()
        )
        val slugFallbackResponse = slugFallbackCity.toResponse("ru")
        assertEquals("nameless-city", slugFallbackResponse.name)
    }
}
