package app.venues.common.util

import com.github.f4b6a3.uuid.UuidCreator
import java.util.*

object IdGenerator {
    /**
     * Generates a time-ordered UUID (v7).
     * This is best for primary keys as it's sequential and prevents
     * database index fragmentation, giving you the performance of a Long
     * with the uniqueness and security of a UUID.
     */
    fun uuidv7(): UUID {
        return UuidCreator.getTimeOrderedEpoch()
    }
}
