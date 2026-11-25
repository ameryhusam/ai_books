package com.example.data.ai

/**
 * Conservative, prompt-boundary redaction for common direct identifiers.
 * It intentionally avoids deleting unlabelled numeric values because lesson content
 * frequently contains equations, measurements, dates, and other meaningful numbers.
 */
object PromptPrivacySanitizer {
    private val emailPattern = Regex(
        "(?i)(?<![A-Z0-9._%+-])[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}(?![A-Z0-9.-])"
    )
    private val labelledPhonePattern = Regex(
        "(?i)((?:رقم\\s+الهاتف|الهاتف|جوال|موبايل|phone|mobile|tel)\\s*[:：]?\\s*)(?:\\+|00)?[0-9][0-9\\s().-]{5,17}[0-9]"
    )
    private val internationalPhonePattern = Regex(
        "(?<![A-Z0-9])(?:\\+|00)[0-9]{1,3}(?:[\\s().-]*[0-9]){7,12}(?![0-9])",
        RegexOption.IGNORE_CASE
    )
    private val labelledIdPattern = Regex(
        "(?i)((?:رقم\\s+الهوية|الهوية\\s+الوطنية|national\\s+id|identity\\s+number)\\s*[:：]?\\s*)[A-Z0-9-]{6,24}"
    )

    fun sanitize(value: String): String {
        if (value.isBlank()) return value
        return value
            .replace(emailPattern, "[REDACTED_EMAIL]")
            .replace(labelledPhonePattern) { match ->
                match.groupValues[1] + "[REDACTED_PHONE]"
            }
            .replace(internationalPhonePattern, "[REDACTED_PHONE]")
            .replace(labelledIdPattern) { match ->
                match.groupValues[1] + "[REDACTED_ID]"
            }
    }
}
