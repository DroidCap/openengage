package com.openengage.core

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

enum class ErrorSeverity {
    INFO, WARNING, ERROR, CRITICAL
}

class OpenEngageConfig private constructor(
    val rageTapCount: Int,
    val rageTapTimeframeMs: Long,
    val rageTapRadiusPx: Float,
    val deadTapTimeframeMs: Long,
    val excludedScreens: Set<String>,
    val maskedApiEndpoints: List<Regex>,
    val eventFilter: ((String) -> Boolean)?
) {

    class Builder {
        private var rageTapCount: Int = 3
        private var rageTapTimeframeMs: Long = 800
        private var rageTapRadiusPx: Float = 100f
        private var deadTapTimeframeMs: Long = 500
        private var excludedScreens: MutableSet<String> = mutableSetOf()
        private var maskedApiEndpoints: MutableList<Regex> = mutableListOf()
        private var eventFilter: ((String) -> Boolean)? = null

        fun setRageTapThreshold(taps: Int, timeframeMs: Long, radiusPx: Float = 100f) = apply {
            this.rageTapCount = taps
            this.rageTapTimeframeMs = timeframeMs
            this.rageTapRadiusPx = radiusPx
        }

        fun setDeadTapThreshold(timeframeMs: Long) = apply {
            this.deadTapTimeframeMs = timeframeMs
        }

        fun excludeScreens(screens: Collection<String>) = apply {
            this.excludedScreens.addAll(screens)
        }

        fun addMaskedApiEndpoints(patterns: Collection<String>) = apply {
            patterns.forEach { pattern ->
                try {
                    this.maskedApiEndpoints.add(Regex(pattern))
                } catch (e: Exception) {
                    // Ignore invalid regexes or print warning
                }
            }
        }

        fun setEventFilter(filter: (String) -> Boolean) = apply {
            this.eventFilter = filter
        }

        fun build(): OpenEngageConfig {
            return OpenEngageConfig(
                rageTapCount = rageTapCount,
                rageTapTimeframeMs = rageTapTimeframeMs,
                rageTapRadiusPx = rageTapRadiusPx,
                deadTapTimeframeMs = deadTapTimeframeMs,
                excludedScreens = excludedScreens,
                maskedApiEndpoints = maskedApiEndpoints,
                eventFilter = eventFilter
            )
        }
    }

    companion object {
        private val jsonParser = Json { ignoreUnknownKeys = true }

        /**
         * Loads configuration from assets/openengage.json if present, falling back to defaults.
         */
        internal fun loadFromAssets(context: Context): OpenEngageConfig {
            val builder = Builder()
            try {
                val jsonString = context.assets.open("openengage.json").bufferedReader().use { it.readText() }
                val parsed = jsonParser.decodeFromString<JsonConfigSchema>(jsonString)
                
                parsed.rage_tap_count?.let { taps ->
                    val timeframe = parsed.rage_tap_threshold_ms ?: 800
                    builder.setRageTapThreshold(taps, timeframe)
                } ?: parsed.rage_tap_threshold_ms?.let { timeframe ->
                    builder.setRageTapThreshold(3, timeframe)
                }

                parsed.dead_tap_threshold_ms?.let { builder.setDeadTapThreshold(it) }
                parsed.excluded_screens?.let { builder.excludeScreens(it) }
                parsed.masked_api_endpoints?.let { builder.addMaskedApiEndpoints(it) }
            } catch (e: IOException) {
                // Config file not found or unreadable, using default configuration builder
            } catch (e: Exception) {
                // Json parsing exception
                android.util.Log.w("OpenEngage", "Failed to parse openengage.json config file: ${e.message}")
            }
            return builder.build()
        }
    }
}

@Serializable
internal data class JsonConfigSchema(
    val rage_tap_threshold_ms: Long? = null,
    val rage_tap_count: Int? = null,
    val dead_tap_threshold_ms: Long? = null,
    val excluded_screens: List<String>? = null,
    val masked_api_endpoints: List<String>? = null
)
