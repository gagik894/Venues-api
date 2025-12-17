package app.venues.shared.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

/**
 * Configuration for async task execution.
 *
 * Enables Spring's async method execution and configures a thread pool
 * for handling background tasks like email sending.
 *
 * Benefits:
 * - Non-blocking API responses
 * - Controlled thread pool for background tasks
 * - Proper queue management to avoid memory issues under high load
 */
@Configuration
@EnableAsync
class AsyncConfig {

    /**
     * Configure thread pool for async tasks.
     *
     * Pool sizing considerations:
     * - corePoolSize: Minimum threads always alive (handles normal load)
     * - maxPoolSize: Maximum threads under high load
     * - queueCapacity: Tasks queued when core threads are busy
     */
    @Bean(name = ["taskExecutor"])
    fun taskExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 5
        executor.queueCapacity = 100
        executor.setThreadNamePrefix("async-email-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.setAwaitTerminationSeconds(30)
        executor.initialize()
        return executor
    }
}
