package com.openengage.core

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenEngageUrlUtilTest {

    @Test
    fun testUrlMaskingWithNoPatterns() {
        val url = "https://example.com/api/v1/users/12345/profile"
        val masked = OpenEngageUrlUtil.maskUrl(url, emptyList())
        assertEquals(url, masked)
    }

    @Test
    fun testUrlMaskingWithExactMatchPattern() {
        val url = "https://example.com/api/v1/users/12345/profile"
        val patterns = listOf(Regex("12345"))
        val masked = OpenEngageUrlUtil.maskUrl(url, patterns)
        assertEquals("https://example.com/api/v1/users/{id}/profile", masked)
    }

    @Test
    fun testUrlMaskingWithRegexPatterns() {
        val url = "https://example.com/api/v1/users/12345/profile"
        val patterns = listOf(
            Regex("users/\\d+/profile"),
            Regex("billing/\\w+")
        )
        val masked = OpenEngageUrlUtil.maskUrl(url, patterns)
        assertEquals("https://example.com/api/v1/{id}", masked)
    }

    @Test
    fun testUrlMaskingMultipleReplacements() {
        val url = "https://example.com/api/v1/users/12345/posts/abc-def"
        val patterns = listOf(
            Regex("users/\\d+"),
            Regex("posts/[a-z\\-]+")
        )
        val masked = OpenEngageUrlUtil.maskUrl(url, patterns)
        assertEquals("https://example.com/api/v1/{id}/{id}", masked)
    }

    @Test
    fun testUrlMaskingNoMatches() {
        val url = "https://example.com/api/v1/health"
        val patterns = listOf(
            Regex("users/\\d+"),
            Regex("posts/[a-z\\-]+")
        )
        val masked = OpenEngageUrlUtil.maskUrl(url, patterns)
        assertEquals(url, masked)
    }
}
