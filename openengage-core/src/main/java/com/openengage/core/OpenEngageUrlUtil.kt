package com.openengage.core

object OpenEngageUrlUtil {
    /**
     * Masks any matching regex patterns in the URL with "{id}" placeholder.
     */
    fun maskUrl(url: String, patterns: List<Regex>): String {
        var masked = url
        patterns.forEach { regex ->
            masked = masked.replace(regex, "{id}")
        }
        return masked
    }
}
