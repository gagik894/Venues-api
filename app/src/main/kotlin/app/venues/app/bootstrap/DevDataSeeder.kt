package app.venues.app.bootstrap

import app.venues.user.domain.User
import app.venues.user.domain.UserRole
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
        seedAdminUser()
    }

    private fun seedAdminUser() {
        if (userRepository.count() == 0L) {
            val admin = User(
                email = "admin@venues.app",
                passwordHash = passwordEncoder.encode("password123"),
                firstName = "System",
                lastName = "Admin",
                phoneNumber = "+1234567890",
            ).apply {
                verifyEmail()
                role = UserRole.ADMIN
            }
            userRepository.save(admin)
            println("✅ Admin seeded: admin@venues.app")
        }
    }
}
