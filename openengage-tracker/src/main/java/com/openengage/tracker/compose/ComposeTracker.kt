package com.openengage.tracker.compose

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.navigation.NavController
import com.openengage.core.OpenEngage

/**
 * CompositionLocal providing active screen context.
 * Allows nested UI elements to resolve their screen source context dynamically.
 */
val LocalOpenEngageScreen = staticCompositionLocalOf { "UnknownScreen" }

/**
 * Automated view tracker for Jetpack Navigation 2.x backstack.
 */
@Composable
fun NavigationViewTrackingEffect(navController: NavController) {
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            val route = destination.route ?: destination.label?.toString() ?: "UnknownRoute"
            OpenEngage.updateActiveScreen(route)
            
            // Log screen enter
            OpenEngage.logScreenEnter(route)
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }
}

/**
 * Automated view tracker for Jetpack Navigation 3 backstack list.
 */
@Composable
fun <T : Any> OpenEngageNavigation3Tracker(
    backStack: List<T>,
    getScreenName: (T) -> String = { it.toString() }
) {
    val currentScreen = backStack.lastOrNull()

    // 1. Monitors active screen entry
    LaunchedEffect(currentScreen) {
        if (currentScreen != null) {
            val screenName = getScreenName(currentScreen)
            OpenEngage.logScreenEnter(screenName)
        }
    }

    // 2. Monitors active screen exit (DisposableEffect tracks disposal lifecycle)
    DisposableEffect(currentScreen) {
        onDispose {
            if (currentScreen != null) {
                val screenName = getScreenName(currentScreen)
                OpenEngage.logScreenExit(screenName)
            }
        }
    }
}

/**
 * Automated view tracker for Jetpack Navigation 3 apps using tabbed/multiple backstacks.
 */
@Composable
fun <T : Any> OpenEngageMultiNavigation3Tracker(
    activeTabId: String,
    activeBackStack: List<T>,
    getScreenName: (T) -> String = { it.toString() }
) {
    val currentScreen = activeBackStack.lastOrNull()

    LaunchedEffect(activeTabId, currentScreen) {
        if (currentScreen != null) {
            OpenEngage.logScreenEnter(
                screenName = getScreenName(currentScreen),
                scope = activeTabId
            )
        }
    }

    DisposableEffect(activeTabId, currentScreen) {
        onDispose {
            if (currentScreen != null) {
                OpenEngage.logScreenExit(
                    screenName = getScreenName(currentScreen),
                    scope = activeTabId
                )
            }
        }
    }
}

/**
 * Helper to traverse the Jetpack Compose Semantics tree at a physical screen coordinate.
 */
object ComposeSemanticsResolver {

    data class ComposableDetails(
        val testTag: String?,
        val text: String?,
        val contentDescription: String?
    )

    fun resolveElement(composeView: View, rawX: Float, rawY: Float): ComposableDetails? {
        val owner = composeView as? RootForTest ?: return null
        val rootNode = owner.semanticsOwner.rootSemanticsNode
        
        // Find coordinates relative to the compose host view
        val location = IntArray(2)
        composeView.getLocationOnScreen(location)
        val relativeX = rawX - location[0]
        val relativeY = rawY - location[1]
        
        val deepestNode = findDeepestSemanticsNode(rootNode, relativeX, relativeY) ?: return null
        
        val testTag = deepestNode.config.getOrNull(SemanticsProperties.TestTag)
        val contentDescriptionList = deepestNode.config.getOrNull(SemanticsProperties.ContentDescription)
        val contentDescription = contentDescriptionList?.joinToString(", ")
        
        val textList = deepestNode.config.getOrNull(SemanticsProperties.Text)
        val text = textList?.joinToString(" ") { it.text }
        
        return ComposableDetails(testTag, text, contentDescription)
    }

    private fun findDeepestSemanticsNode(node: SemanticsNode, relativeX: Float, relativeY: Float): SemanticsNode? {
        val bounds = node.boundsInRoot
        if (!bounds.contains(Offset(relativeX, relativeY))) {
            return null
        }
        
        val children = node.children
        for (i in children.size - 1 downTo 0) {
            val child = children[i]
            val found = findDeepestSemanticsNode(child, relativeX, relativeY)
            if (found != null) return found
        }
        return node
    }
}
