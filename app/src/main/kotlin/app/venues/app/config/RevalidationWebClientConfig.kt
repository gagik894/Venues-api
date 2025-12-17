package app.venues.app.config

import io.netty.channel.ChannelOption
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient

@Configuration
class RevalidationWebClientConfig {

    @Bean
    fun revalidationWebClient(properties: FrontendRevalidationProperties): WebClient {
        val timeoutMillis = properties.timeout.toMillis().toInt()
        val httpClient = HttpClient.create()
            .responseTimeout(properties.timeout)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutMillis)

        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }
}
