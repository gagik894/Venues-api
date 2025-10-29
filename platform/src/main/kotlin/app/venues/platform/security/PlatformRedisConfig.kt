package app.venues.platform.security

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

/**
 * Redis configuration for platform nonce caching.
 *
 * Provides distributed cache for nonce replay attack prevention.
 * Nonces are stored with TTL matching timestamp validation window.
 */
@Configuration
@ConfigurationProperties(prefix = "platform.redis")
class PlatformRedisConfig {

    var host: String = "localhost"
    var port: Int = 6379
    var password: String? = null
    var database: Int = 0
    var timeout: Duration = Duration.ofSeconds(2)

    @Bean
    fun platformRedisConnectionFactory(): RedisConnectionFactory {
        val redisConfig = RedisStandaloneConfiguration().apply {
            hostName = host
            port = this@PlatformRedisConfig.port
            database = this@PlatformRedisConfig.database
            password?.let { password = it }
        }

        return LettuceConnectionFactory(redisConfig).apply {
            timeout = timeout
        }
    }

    @Bean
    fun platformRedisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, String> {
        return RedisTemplate<String, String>().apply {
            setConnectionFactory(connectionFactory)
            keySerializer = StringRedisSerializer()
            valueSerializer = StringRedisSerializer()
            hashKeySerializer = StringRedisSerializer()
            hashValueSerializer = StringRedisSerializer()
            afterPropertiesSet()
        }
    }
}

