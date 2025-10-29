package app.venues.platform.webhook

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Configuration for WebClient used in webhook delivery.
 */
@Configuration
class WebhookWebClientConfig {

    companion object {
        private const val CONNECT_TIMEOUT_MILLIS = 5000
        private const val READ_TIMEOUT_SECONDS = 10L
        private const val WRITE_TIMEOUT_SECONDS = 10L
        private const val RESPONSE_TIMEOUT_SECONDS = 10L
    }

    @Bean
    fun webhookWebClient(): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
            .responseTimeout(Duration.ofSeconds(RESPONSE_TIMEOUT_SECONDS))
            .doOnConnected { conn ->
                conn.addHandlerLast(ReadTimeoutHandler(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .addHandlerLast(WriteTimeoutHandler(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            }

        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }
}

