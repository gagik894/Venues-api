package app.venues.seating.domain

import app.venues.seating.model.BackgroundTransform
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

object BackgroundTransformMapper {
    private val mapper = jacksonObjectMapper()

    fun toJson(transform: BackgroundTransform?): String? {
        return transform?.let { mapper.writeValueAsString(it) }
    }

    fun fromJson(json: String?): BackgroundTransform? {
        if (json.isNullOrBlank()) return null
        return mapper.readValue(json)
    }
}

