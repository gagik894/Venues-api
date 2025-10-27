/*
 * Venues API - Government-Sponsored Cultural Venues Portal
 * Copyright (c) 2025 Government Cultural Department
 *
 * Validation utility functions for common business rules.
 */

package app.venues.common.util

import app.venues.common.constants.AppConstants

/**
 * Validation utilities for common data validation patterns.
 *
 * These utilities provide reusable validation logic that can be applied
 * across different modules without introducing framework dependencies.
 */
object ValidationUtil {

    /**
     * Email validation regex pattern.
     * Compliant with RFC 5322 simplified pattern.
     */
    private val EMAIL_PATTERN = Regex(AppConstants.Patterns.EMAIL)

    /**
     * Phone number validation regex pattern.
     * Accepts international formats with optional country code.
     */
    private val PHONE_PATTERN = Regex(AppConstants.Patterns.PHONE)

    /**
     * URL validation regex pattern.
     * Validates HTTP and HTTPS URLs.
     */
    private val URL_PATTERN = Regex(AppConstants.Patterns.URL)

    /**
     * Validates an email address format.
     *
     * @param email The email address to validate
     * @return true if the email format is valid, false otherwise
     */
    fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() && EMAIL_PATTERN.matches(email.trim())
    }

    /**
     * Validates a phone number format.
     *
     * @param phone The phone number to validate
     * @return true if the phone format is valid, false otherwise
     */
    fun isValidPhone(phone: String): Boolean {
        return phone.isNotBlank() && PHONE_PATTERN.matches(phone.trim())
    }

    /**
     * Validates a URL format.
     *
     * @param url The URL to validate
     * @return true if the URL format is valid, false otherwise
     */
    fun isValidUrl(url: String): Boolean {
        return url.isNotBlank() && URL_PATTERN.matches(url.trim())
    }

    /**
     * Validates that a string is not blank and meets minimum/maximum length requirements.
     *
     * @param value The string to validate
     * @param minLength Minimum required length (inclusive)
     * @param maxLength Maximum allowed length (inclusive)
     * @return true if the string meets the requirements, false otherwise
     */
    fun isValidLength(value: String, minLength: Int = 1, maxLength: Int = Int.MAX_VALUE): Boolean {
        val trimmed = value.trim()
        return trimmed.length in minLength..maxLength
    }

    /**
     * Validates that a numeric value is within a specified range.
     *
     * @param value The numeric value to validate
     * @param min Minimum allowed value (inclusive)
     * @param max Maximum allowed value (inclusive)
     * @return true if the value is within range, false otherwise
     */
    fun isInRange(value: Int, min: Int, max: Int): Boolean {
        return value in min..max
    }

    /**
     * Validates that a numeric value is within a specified range.
     *
     * @param value The numeric value to validate
     * @param min Minimum allowed value (inclusive)
     * @param max Maximum allowed value (inclusive)
     * @return true if the value is within range, false otherwise
     */
    fun isInRange(value: Long, min: Long, max: Long): Boolean {
        return value in min..max
    }

    /**
     * Validates that a numeric value is positive (greater than zero).
     *
     * @param value The numeric value to validate
     * @return true if the value is positive, false otherwise
     */
    fun isPositive(value: Int): Boolean = value > 0

    /**
     * Validates that a numeric value is positive (greater than zero).
     *
     * @param value The numeric value to validate
     * @return true if the value is positive, false otherwise
     */
    fun isPositive(value: Long): Boolean = value > 0

    /**
     * Validates that a numeric value is non-negative (zero or greater).
     *
     * @param value The numeric value to validate
     * @return true if the value is non-negative, false otherwise
     */
    fun isNonNegative(value: Int): Boolean = value >= 0

    /**
     * Validates that a numeric value is non-negative (zero or greater).
     *
     * @param value The numeric value to validate
     * @return true if the value is non-negative, false otherwise
     */
    fun isNonNegative(value: Long): Boolean = value >= 0
}

