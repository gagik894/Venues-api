package app.venues.event.api.mapper

import app.venues.event.domain.EventCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EventMapperTest {

    private val mapper = EventMapper()

    @Test
    fun `toCategoryResponse uses requested language`() {
        val category = EventCategory(
            code = "THEATER",
            names = mapOf("en" to "Theater", "hy" to "Թատրոն"),
            color = "#000000",
            icon = "mask",
            displayOrder = 1,
            isActive = true
        ).also { it.id = 1L }

        val dto = mapper.toCategoryResponse(category, "hy")

        assertEquals("Թատրոն", dto.name)
        assertEquals(category.names, dto.names)
    }

    @Test
    fun `toCategoryResponse falls back to code when translation missing`() {
        val category = EventCategory(
            code = "JAZZ",
            names = emptyMap(),
            color = null,
            icon = null,
            displayOrder = 2,
            isActive = true
        ).also { it.id = 2L }

        val dto = mapper.toCategoryResponse(category, "ru")

        assertEquals("JAZZ", dto.name)
    }
}
