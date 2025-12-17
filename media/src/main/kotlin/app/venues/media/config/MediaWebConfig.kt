package app.venues.media.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.file.Paths

@Configuration
class MediaWebConfig(
    private val mediaProperties: MediaProperties
) : WebMvcConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val uploadPath = Paths.get(mediaProperties.uploadDir).toAbsolutePath().toUri().toString()

        registry
            .addResourceHandler("/uploads/**")
            .addResourceLocations(uploadPath)
            .setCachePeriod(3600)
    }
}

