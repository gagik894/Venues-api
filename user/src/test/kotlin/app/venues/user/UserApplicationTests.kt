package app.venues.user

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

/**
 * Integration test for verifying Spring application context loads correctly.
 * Disabled until test database configuration (Testcontainers or embedded DB) is set up.
 */
@SpringBootTest
@Disabled("Requires database connection - enable when Testcontainers is configured")
class UserApplicationTests {

    @Test
    fun contextLoads() {
    }
}
