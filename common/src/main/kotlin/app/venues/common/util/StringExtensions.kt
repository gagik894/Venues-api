package app.venues.common.util

/**
 * Extension functions for String class providing common utility operations.
 * These extensions follow Kotlin idiomatic patterns and promote code reusability.
 */

/**
 * Checks if a string is blank or null.
 *
 * @return true if the string is null or contains only whitespace, false otherwise
 */
fun String?.isNullOrBlankString(): Boolean {
    return this.isNullOrBlank()
}

/**
 * Checks if a string is not blank and not null.
 *
 * @return true if the string is not null and contains non-whitespace characters
 */
fun String?.isNotNullOrBlank(): Boolean {
    return !this.isNullOrBlank()
}

/**
 * Safely trims a string, returning null if the input is null.
 *
 * @return trimmed string or null
 */
fun String?.trimOrNull(): String? {
    return this?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * Converts a string to snake_case format.
 *
 * Example: "UserProfile" -> "user_profile"
 *
 * @return snake_case formatted string
 */
fun String.toSnakeCase(): String {
    return this.replace(Regex("([a-z])([A-Z])"), "$1_$2")
        .replace(Regex("([A-Z])([A-Z][a-z])"), "$1_$2")
        .lowercase()
}

/**
 * Converts a string to kebab-case format.
 *
 * Example: "UserProfile" -> "user-profile"
 *
 * @return kebab-case formatted string
 */
fun String.toKebabCase(): String {
    return this.replace(Regex("([a-z])([A-Z])"), "$1-$2")
        .replace(Regex("([A-Z])([A-Z][a-z])"), "$1-$2")
        .lowercase()
}

/**
 * Capitalizes the first letter of the string.
 *
 * @return string with first letter capitalized
 */
fun String.capitalizeFirst(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

/**
 * Masks sensitive information in a string, showing only first and last characters.
 *
 * Example: "secret123" -> "s*****3"
 *
 * @param visibleChars Number of visible characters at start and end (default: 1)
 * @param maskChar Character to use for masking (default: *)
 * @return masked string
 */
fun String.mask(visibleChars: Int = 1, maskChar: Char = '*'): String {
    if (this.length <= visibleChars * 2) {
        return maskChar.toString().repeat(this.length)
    }
    val start = this.take(visibleChars)
    val end = this.takeLast(visibleChars)
    val masked = maskChar.toString().repeat(this.length - visibleChars * 2)
    return "$start$masked$end"
}

/**
 * Truncates a string to a specified length and adds ellipsis if needed.
 *
 * @param maxLength Maximum length of the resulting string (including ellipsis)
 * @param ellipsis String to append when truncating (default: "...")
 * @return truncated string with ellipsis if applicable
 */
fun String.truncate(maxLength: Int, ellipsis: String = "..."): String {
    if (this.length <= maxLength) return this
    val truncateLength = (maxLength - ellipsis.length).coerceAtLeast(0)
    return this.take(truncateLength) + ellipsis
}

/**
 * Removes all whitespace from a string.
 *
 * @return string without any whitespace characters
 */
fun String.removeWhitespace(): String {
    return this.replace(Regex("\\s+"), "")
}

/**
 * Checks if a string contains only alphanumeric characters.
 *
 * @return true if string contains only letters and digits, false otherwise
 */
fun String.isAlphanumeric(): Boolean {
    return this.matches(Regex("^[a-zA-Z0-9]+$"))
}

/**
 * Checks if a string contains only alphabetic characters.
 *
 * @return true if string contains only letters, false otherwise
 */
fun String.isAlphabetic(): Boolean {
    return this.matches(Regex("^[a-zA-Z]+$"))
}

/**
 * Checks if a string contains only numeric characters.
 *
 * @return true if string contains only digits, false otherwise
 */
fun String.isNumeric(): Boolean {
    return this.matches(Regex("^[0-9]+$"))
}

