package app.venues.location.api.controller

import app.venues.location.api.dto.CreateCityRequest
import app.venues.location.api.dto.CreateRegionRequest
import app.venues.location.api.dto.RegionResponse
import app.venues.location.service.LocationService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AdminLocationController::class)
class AdminLocationControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper
) {

    @MockBean
    lateinit var locationService: LocationService

    @Test
    @WithMockUser(roles = ["SUPER_ADMIN"])
    fun `get all regions returns data`() {
        val regions = listOf(
            RegionResponse(
                code = "AM-ER",
                names = mapOf("en" to "Yerevan"),
                name = "Yerevan",
                displayOrder = 1,
                isActive = true
            )
        )
        given(locationService.getAllRegions()).willReturn(regions)

        mockMvc.perform(get("/api/v1/locations/admin/regions"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].code").value("AM-ER"))

        verify(locationService).getAllRegions()
    }

    @Test
    @WithMockUser(roles = ["SUPER_ADMIN"])
    fun `create region returns created response`() {
        val request = CreateRegionRequest(
            code = "AM-KT",
            names = mapOf("en" to "Kotayk", "hy" to "Կոտայք"),
            displayOrder = 2
        )
        val response = RegionResponse(
            code = request.code,
            names = request.names,
            name = "Kotayk",
            displayOrder = 2,
            isActive = true
        )
        given(locationService.createRegion(request)).willReturn(response)

        mockMvc.perform(
            post("/api/v1/locations/admin/regions")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.code").value("AM-KT"))

        verify(locationService).createRegion(request)
    }

    @Test
    @WithMockUser(roles = ["SUPER_ADMIN"])
    fun `update region uses code path variable`() {
        val request = mapOf("displayOrder" to 3)
        val region = RegionResponse(
            code = "AM-ER",
            names = mapOf("en" to "Yerevan"),
            name = "Yerevan",
            displayOrder = 3,
            isActive = true
        )
        given(locationService.updateRegion(eq("AM-ER"), any())).willReturn(region)

        mockMvc.perform(
            put("/api/v1/locations/admin/regions/AM-ER")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.displayOrder").value(3))

        verify(locationService).updateRegion(eq("AM-ER"), any())
    }

    @Test
    @WithMockUser(roles = ["SUPER_ADMIN"])
    fun `create city hits admin route`() {
        val request = CreateCityRequest(
            regionCode = "AM-ER",
            slug = "yerevan",
            names = mapOf("en" to "Yerevan", "hy" to "Երևան"),
            officialId = null,
            displayOrder = 1
        )
        val cityResponse = app.venues.location.api.dto.CityResponse(
            slug = request.slug,
            names = request.names,
            name = "Yerevan",
            region = app.venues.location.api.dto.RegionCompact(code = "AM-ER", name = "Yerevan"),
            officialId = null,
            displayOrder = 1,
            isActive = true
        )
        given(locationService.createCity(request)).willReturn(cityResponse)

        mockMvc.perform(
            post("/api/v1/locations/admin/cities")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.slug").value("yerevan"))

        verify(locationService).createCity(request)
    }
}

