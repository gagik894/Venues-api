package app.venues.app.bootstrap

import app.venues.user.domain.User
import app.venues.user.repository.UserRepository
import app.venues.venue.repository.VenueRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
@Profile("default", "dev")
class DevDataSeeder(
    private val userRepository: UserRepository,
    private val venueRepository: VenueRepository,
    private val passwordEncoder: PasswordEncoder
) {

    @Bean
    fun seedInitialData() = CommandLineRunner {
        seedTestUser()
    }

    private fun seedTestUser() {
        if (userRepository.count() == 0L) {
            val testUser = User(
                email = "customer@venues.app",
                passwordHash = passwordEncoder.encode("password123"),
                firstName = "Test",
                lastName = "Customer",
                phoneNumber = "+1234567890",
            ).apply {
                verifyEmail()
            }
            userRepository.save(testUser)
            println("✅ Test customer seeded: customer@venues.app")
        }
    }
}
