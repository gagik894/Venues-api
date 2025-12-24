package app.venues.shared.scheduling

import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Adaptive scheduled task wrapper that tracks work found and uses exponential backoff.
 *
 * This allows Neon DB to scale to 0 by skipping task execution when no work is found
 * for consecutive runs. Tasks will only execute when:
 * 1. Work is found (resets skip counter)
 * 2. Maximum skip count hasn't been reached
 *
 * Strategy:
 * - If work found: Reset skip counter, continue normal execution
 * - If no work found: Increment skip counter
 * - If skip counter >= maxSkips: Skip execution (don't connect to DB)
 * - After maxSkips reached, check periodically (every N minutes) if work exists
 *
 * This ensures:
 * - When idle: Tasks skip execution → No DB connections → Neon scales to 0
 * - When work exists: Tasks run normally → Cleanup happens promptly
 */
class AdaptiveScheduledTask(
    private val taskName: String,
    private val maxConsecutiveSkips: Int = 5, // Skip 5 runs = 5 minutes of inactivity
    private val checkIntervalAfterMaxSkips: Long = 3600000 // Check every 60 minutes after max skips
) {
    private val logger = KotlinLogging.logger {}
    private val consecutiveNoWorkCount = AtomicInteger(0)
    private val lastWorkFoundAt = AtomicReference<Instant?>(null)
    private val lastExecutionAt = AtomicReference<Instant?>(null)

    /**
     * Executes the task if conditions are met (not skipped due to no work).
     *
     * @param task The actual task to execute
     * @return Result indicating if task executed and if work was found
     */
    fun executeIfNeeded(task: () -> Int): ExecutionResult {
        val now = Instant.now()
        lastExecutionAt.set(now)

        // Check if we should skip execution
        val skipCount = consecutiveNoWorkCount.get()
        if (skipCount >= maxConsecutiveSkips) {
            // After max skips, only check periodically
            val lastExec = lastExecutionAt.get()
            if (lastExec != null) {
                val timeSinceLastExec = java.time.Duration.between(lastExec, now).toMillis()
                if (timeSinceLastExec < checkIntervalAfterMaxSkips) {
                    logger.debug { "$taskName: Skipping (no work found in last $skipCount runs, next check in ${(checkIntervalAfterMaxSkips - timeSinceLastExec) / 1000}s)" }
                    return ExecutionResult.skipped(skipCount)
                }
            }
        }

        // Execute the task
        val workCount = try {
            task()
        } catch (e: Exception) {
            logger.error(e) { "$taskName: Error during execution" }
            // On error, don't increment skip count (might be transient)
            return ExecutionResult.executed(0, false)
        }

        val workFound = workCount > 0

        if (workFound) {
            // Work found: reset skip counter
            consecutiveNoWorkCount.set(0)
            lastWorkFoundAt.set(now)
            logger.debug { "$taskName: Work found ($workCount items), reset skip counter" }
            return ExecutionResult.executed(workCount, true)
        } else {
            // No work: increment skip counter
            val newSkipCount = consecutiveNoWorkCount.incrementAndGet()
            logger.debug { "$taskName: No work found, skip count: $newSkipCount/$maxConsecutiveSkips" }
            return ExecutionResult.executed(0, false)
        }
    }

    /**
     * Result of task execution attempt.
     */
    data class ExecutionResult(
        val executed: Boolean,
        val workCount: Int,
        val workFound: Boolean,
        val skipCount: Int
    ) {
        companion object {
            fun executed(workCount: Int, workFound: Boolean, skipCount: Int = 0) =
                ExecutionResult(true, workCount, workFound, skipCount)

            fun skipped(skipCount: Int) =
                ExecutionResult(false, 0, false, skipCount)
        }
    }
}

