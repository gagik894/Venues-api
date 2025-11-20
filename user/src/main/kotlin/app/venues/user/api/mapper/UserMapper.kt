package app.venues.user.api.mapper

import app.venues.user.api.dto.UserResponse
import app.venues.user.domain.User

/**
 * Mapper object for converting between User entities and DTOs.
 *
 * Responsibilities:
 * - Convert entities to DTOs for API responses
 * - Ensure sensitive data (passwords) never leaves the domain layer
 * - Provide consistent transformation logic
 *
 * Design Pattern: Mapper Pattern
 * - Separates domain entities from API contracts
 * - Allows evolution of internal model without breaking API
 * - Centralizes conversion logic
 */
object UserMapper {

    /**
     * Converts a User entity to UserResponse DTO.
     *
     * Filters out sensitive information:
     * - Password hash
     * - Failed login attempts
     * - Lock information
     *
     * @param user The User entity to convert
     * @return UserResponse DTO safe for API responses
     */
    fun toResponse(user: User): UserResponse {
        return UserResponse(
            id = user.id,
            email = user.email,
            firstName = user.firstName,
            lastName = user.lastName,
            fullName = user.getFullName(),
            phoneNumber = user.phoneNumber,
            status = user.status,
            emailVerified = user.emailVerified,
            lastLoginAt = user.lastLoginAt,
            createdAt = user.createdAt,
            lastModifiedAt = user.lastModifiedAt
        )
    }

    /**
     * Converts a list of User entities to UserResponse DTOs.
     *
     * @param users List of User entities
     * @return List of UserResponse DTOs
     */
    fun toResponseList(users: List<User>): List<UserResponse> {
        return users.map { toResponse(it) }
    }
}

