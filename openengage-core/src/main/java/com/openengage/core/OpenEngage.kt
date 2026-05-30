package com.openengage.core

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log

typealias EventProcessor = (OpenEngageEvent) -> OpenEngageEvent?

class OpenEngageEvent(
    val name: String,
    val parameters: MutableMap<String, Any>
) {
    fun putParameter(key: String, value: String) { parameters[key] = value }
    fun putParameter(key: String, value: Long) { parameters[key] = value }
    fun putParameter(key: String, value: Double) { parameters[key] = value }
    
    fun getStringParameter(key: String): String? = parameters[key] as? String
    fun getLongParameter(key: String): Long? = parameters[key] as? Long
    fun getDoubleParameter(key: String): Double? = parameters[key] as? Double
}

@SuppressLint("StaticFieldLeak")
object OpenEngage {
    private const val TAG = "OpenEngage"
    
    private var context: Context? = null
    private var config: OpenEngageConfig? = null
    private var firebaseAnalytics: Any? = null
    
    @Volatile
    private var activeScreen: String = "UnknownScreen"
    
    @Volatile
    private var activeScope: String? = null

    private val eventProcessors = mutableListOf<EventProcessor>()
    
    private val screenTimestamps = mutableMapOf<String, Long>()

    @Volatile
    var onUserActionDetected: (() -> Unit)? = null

    /**
     * Call this when a user action or network call takes place to reset dead-tap evaluations.
     */
    fun notifyUserAction() {
        onUserActionDetected?.invoke()
    }

    /**
     * Initializes the OpenEngage SDK. If no config is provided, it tries to load from assets.
     */
    @Synchronized
    fun initialize(context: Context, config: OpenEngageConfig? = null) {
        if (this.context != null) {
            Log.d(TAG, "SDK already initialized.")
            return
        }
        
        val appContext = context.applicationContext
        this.context = appContext
        this.config = config ?: OpenEngageConfig.loadFromAssets(appContext)
        
        // Attempt to capture Firebase Analytics instance
        try {
            firebaseAnalytics = com.google.firebase.analytics.FirebaseAnalytics.getInstance(appContext)
            Log.i(TAG, "Successfully attached to Firebase Analytics.")
        } catch (e: NoClassDefFoundError) {
            Log.w(TAG, "Firebase Analytics SDK not found. Logs will not be sent.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase Analytics: ${e.message}")
        }
    }

    fun getConfig(): OpenEngageConfig? = config

    /**
     * Updates the current active screen name and scope.
     */
    fun updateActiveScreen(screenName: String, scope: String? = null) {
        activeScreen = screenName
        activeScope = scope
    }

    fun getActiveScreen(): String = activeScreen
    fun getActiveScope(): String? = activeScope

    /**
     * Registers a global event interceptor/processor (middleware).
     */
    fun addGlobalEventProcessor(processor: EventProcessor) {
        synchronized(eventProcessors) {
            eventProcessors.add(processor)
        }
    }

    /**
     * Logs a custom screen enter event.
     */
    fun logScreenEnter(screenName: String, scope: String? = null) {
        updateActiveScreen(screenName, scope)
        screenTimestamps[screenName] = System.currentTimeMillis()
        
        logEvent("oe_screen_enter") {
            // Already includes default screen parameters
        }
    }

    /**
     * Logs a screen exit event, calculating the duration.
     */
    fun logScreenExit(screenName: String, scope: String? = null) {
        val enterTime = screenTimestamps.remove(screenName)
        val timeSpent = if (enterTime != null) System.currentTimeMillis() - enterTime else 0L
        
        logEvent("oe_screen_exit") {
            putParameter("oe_time_spent_ms", timeSpent)
            scope?.let { putParameter("oe_screen_scope", it) }
        }
    }

    /**
     * Logs custom developer errors.
     */
    fun logError(
        message: String,
        throwable: Throwable? = null,
        severity: ErrorSeverity = ErrorSeverity.ERROR,
        parametersBuilder: (OpenEngageEvent.() -> Unit)? = null
    ) {
        logEvent("oe_custom_error") {
            putParameter("oe_error_message", message)
            putParameter("oe_severity", severity.name)
            throwable?.let {
                putParameter("oe_error_class", it.javaClass.simpleName)
            }
            parametersBuilder?.invoke(this)
        }
    }

    /**
     * Standard internal logger. Processes filters, appends default metadata, and dispatches to GA4.
     */
    fun logEvent(name: String, parametersBuilder: (OpenEngageEvent.() -> Unit)? = null) {
        val cfg = config
        // 1. Evaluate event filter
        if (cfg?.eventFilter != null && !cfg.eventFilter.invoke(name)) {
            return
        }

        // 2. Build parameter list
        val params = mutableMapOf<String, Any>()
        val sdkEvent = OpenEngageEvent(name, params)
        
        // Populate standard default parameters
        sdkEvent.putParameter("oe_tracker", "openengage")
        sdkEvent.putParameter("oe_timestamp_ms", System.currentTimeMillis())
        sdkEvent.putParameter("oe_screen_name", activeScreen)
        activeScope?.let { sdkEvent.putParameter("oe_screen_scope", it) }
        
        // Execute event custom parameters builder
        parametersBuilder?.invoke(sdkEvent)

        // 3. Execute global event processors
        var processedEvent: OpenEngageEvent? = sdkEvent
        synchronized(eventProcessors) {
            for (processor in eventProcessors) {
                processedEvent = processedEvent?.let { processor.invoke(it) }
                if (processedEvent == null) break
            }
        }

        val eventToLog = processedEvent ?: return // Dropped by processor

        // 4. Send to Firebase Analytics
        val fa = firebaseAnalytics
        if (fa is com.google.firebase.analytics.FirebaseAnalytics) {
            val bundle = Bundle()
            eventToLog.parameters.forEach { (key, value) ->
                when (value) {
                    is String -> bundle.putString(key, value)
                    is Long -> bundle.putLong(key, value)
                    is Int -> bundle.putLong(key, value.toLong())
                    is Double -> bundle.putDouble(key, value)
                    is Float -> bundle.putDouble(key, value.toDouble())
                    is Boolean -> bundle.putString(key, value.toString())
                }
            }
            fa.logEvent(eventToLog.name, bundle)
            Log.d(TAG, "Sent event: ${eventToLog.name} with params: ${eventToLog.parameters}")
        } else {
            Log.d(TAG, "Mock logged event: ${eventToLog.name} with params: ${eventToLog.parameters}")
        }
    }
}
