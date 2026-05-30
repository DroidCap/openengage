package com.openengage.tracker.xml

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import com.openengage.core.OpenEngage
import java.lang.ref.WeakReference
import kotlin.math.hypot

object XmlTracker : Application.ActivityLifecycleCallbacks {

    private val handler = Handler(Looper.getMainLooper())
    private val activeActivities = mutableMapOf<String, WeakReference<Activity>>()
    
    // Tap detector for rage tap detection
    private var rageTapDetector: com.openengage.core.RageTapDetector? = null
    
    // Last user action trackers for dead tap evaluation
    private var lastActionTimestamp = 0L
    private var pendingDeadTapCheck: Runnable? = null

    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
        OpenEngage.onUserActionDetected = { registerNetworkOrNavigationEvent() }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        val activityName = activity.javaClass.simpleName
        activeActivities[activityName] = WeakReference(activity)
        
        // Intercept Touch events by wrapping Window Callback
        val window = activity.window
        val originalCallback = window.callback
        window.callback = WindowCallbackWrapper(originalCallback, activity, activityName)
    }

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityResumed(activity: Activity) {
        val screenName = activity.javaClass.simpleName
        OpenEngage.logScreenEnter(screenName)
    }

    override fun onActivityPaused(activity: Activity) {
        val screenName = activity.javaClass.simpleName
        OpenEngage.logScreenExit(screenName)
    }

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        activeActivities.remove(activity.javaClass.simpleName)
    }

    // --- Action tracking ---

    internal fun registerNetworkOrNavigationEvent() {
        lastActionTimestamp = System.currentTimeMillis()
        // Cancel pending dead tap checks because some action occurred
        pendingDeadTapCheck?.let { handler.removeCallbacks(it) }
        pendingDeadTapCheck = null
    }

    internal fun handleBackPress(screenName: String) {
        registerNetworkOrNavigationEvent()
        
        // Track consecutive back presses for navigation spam anomaly
        val now = System.currentTimeMillis()
        BackSpamTracker.registerBackPress(screenName, now)
    }

    private fun resolveElementDetails(clickedView: View?, x: Float, y: Float): ElementDetails {
        if (clickedView == null) {
            return ElementDetails("unknown_view", "View", "XML")
        }

        val className = clickedView.javaClass.name
        if (className.contains("AndroidComposeView")) {
            try {
                val details = com.openengage.tracker.compose.ComposeSemanticsResolver.resolveElement(clickedView, x, y)
                if (details != null) {
                    val viewId = details.testTag ?: details.text ?: details.contentDescription ?: "compose_composable"
                    return ElementDetails(viewId, "Composable", "Compose")
                }
            } catch (e: Throwable) {
                // Fall back to standard view details if Compose resolver fails or isn't present
            }
        }

        val viewId = getViewId(clickedView)
        val viewType = clickedView.javaClass.simpleName
        return ElementDetails(viewId, viewType, "XML")
    }

    data class ElementDetails(
        val viewId: String,
        val viewType: String,
        val framework: String
    )

    internal fun handleTouchEvent(activity: Activity, screenName: String, event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN) return
        
        val x = event.rawX
        val y = event.rawY
        val now = System.currentTimeMillis()
        
        val config = OpenEngage.getConfig() ?: return
        
        // 1. Process Rage Tap
        val detector = rageTapDetector ?: com.openengage.core.RageTapDetector(
            rageTapCount = config.rageTapCount,
            rageTapTimeframeMs = config.rageTapTimeframeMs,
            rageTapRadiusPx = config.rageTapRadiusPx
        ).also { rageTapDetector = it }
        
        val rageTapCount = detector.registerTap(x, y, now)
        if (rageTapCount != null) {
            val clickedView = findDeepestView(activity, x.toInt(), y.toInt())
            val details = resolveElementDetails(clickedView, x, y)
            
            OpenEngage.logEvent("oe_rage_tap") {
                putParameter("oe_target_view_id", details.viewId)
                putParameter("oe_target_view_type", details.viewType)
                putParameter("oe_tap_count", rageTapCount.toLong())
                putParameter("oe_layout_framework", details.framework)
            }
            return
        }

        // 2. Process Dead Tap Evaluation
        val clickedView = findDeepestView(activity, x.toInt(), y.toInt())
        val isClickable = clickedView?.let { it.isClickable || it.isLongClickable } ?: false
        
        val checkDeadTap = Runnable {
            val details = resolveElementDetails(clickedView, x, y)
            val elapsed = System.currentTimeMillis() - lastActionTimestamp
            if (elapsed >= config.deadTapTimeframeMs) {
                OpenEngage.logEvent("oe_dead_tap") {
                    putParameter("oe_target_view_id", details.viewId)
                    putParameter("oe_target_view_type", details.viewType)
                    putParameter("oe_layout_framework", details.framework)
                }
            }
        }
        
        pendingDeadTapCheck?.let { handler.removeCallbacks(it) }
        pendingDeadTapCheck = checkDeadTap
        
        handler.postDelayed(checkDeadTap, config.deadTapTimeframeMs)
    }

    // --- Layout view hierarchy traversal ---

    private fun findDeepestView(activity: Activity, x: Int, y: Int): View? {
        val root = activity.window.decorView.rootView
        return findDeepestViewAt(root, x, y)
    }

    private fun findDeepestViewAt(view: View, x: Int, y: Int): View? {
        if (view.visibility != View.VISIBLE || !viewContains(view, x, y)) return null
        
        if (view is ViewGroup) {
            // Traverse from top elements (last children) first
            for (i in view.childCount - 1 downTo 0) {
                val child = view.getChildAt(i)
                val found = findDeepestViewAt(child, x, y)
                if (found != null) return found
            }
        }
        return view
    }

    private fun viewContains(view: View, x: Int, y: Int): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val viewX = location[0]
        val viewY = location[1]
        return x >= viewX && x <= viewX + view.width && y >= viewY && y <= viewY + view.height
    }

    private fun getViewId(view: View): String {
        return try {
            if (view.id != View.NO_ID && view.resources != null) {
                view.resources.getResourceEntryName(view.id)
            } else {
                "${view.javaClass.simpleName}_at_coord"
            }
        } catch (e: Exception) {
            "${view.javaClass.simpleName}_raw"
        }
    }
}

/**
 * Clean wrapper of Window.Callback using Kotlin Delegation to keep custom callbacks working.
 */
private class WindowCallbackWrapper(
    private val delegate: Window.Callback,
    private val activity: Activity,
    private val screenName: String
) : Window.Callback by delegate {

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        if (event != null) {
            XmlTracker.handleTouchEvent(activity, screenName, event)
        }
        return delegate.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event != null && event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
            XmlTracker.handleBackPress(screenName)
        }
        return delegate.dispatchKeyEvent(event)
    }
}

/**
 * Tracks physical back button pressing spams
 */
private object BackSpamTracker {
    private val backPressTimestamps = mutableListOf<Long>()

    fun registerBackPress(screenName: String, timestamp: Long) {
        backPressTimestamps.add(timestamp)
        // Keep within 2 seconds
        backPressTimestamps.removeAll { timestamp - it > 2000 }
        
        if (backPressTimestamps.size >= 3) {
            backPressTimestamps.clear()
            OpenEngage.logEvent("oe_navigation_anomaly") {
                putParameter("oe_screen_name", screenName)
                putParameter("oe_anomaly_type", "BACK_BUTTON_SPAM")
            }
        }
    }
}
