package com.openengage.sample

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openengage.core.OpenEngage
import com.openengage.okhttp.OpenEngageOkHttpInterceptor
import com.openengage.tracker.compose.LocalOpenEngageScreen
import com.openengage.tracker.compose.OpenEngageMultiNavigation3Tracker
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .addInterceptor(OpenEngageOkHttpInterceptor())
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OpenEngageTheme {
                MainContentScreen(okHttpClient = okHttpClient)
            }
        }
    }
}

// Sleek dark-mode aesthetic theme
@Composable
fun OpenEngageTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF00ADB5),
            background = Color(0xFF222831),
            surface = Color(0xFF393E46),
            onPrimary = Color.White,
            onBackground = Color(0xFFEEEEEE),
            onSurface = Color(0xFFEEEEEE)
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContentScreen(okHttpClient: OkHttpClient) {
    var selectedTab by remember { mutableStateOf("Dashboard") }
    val mockHomeBackStack = remember { mutableStateListOf("HomeRoot") }
    val mockCartBackStack = remember { mutableStateListOf("CartRoot") }
    val mockProfileBackStack = remember { mutableStateListOf("ProfileRoot") }

    // Multi-Backstack observer tracking tab changes
    val activeStack = when (selectedTab) {
        "Dashboard" -> mockHomeBackStack
        "Cart" -> mockCartBackStack
        else -> mockProfileBackStack
    }
    OpenEngageMultiNavigation3Tracker(
        activeTabId = selectedTab,
        activeBackStack = activeStack
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                listOf("Dashboard", "Cart", "Profile").forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Text(tab[0].toString(), fontWeight = FontWeight.Bold) },
                        label = { Text(tab) }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF1E2022), Color(0xFF2C3E50))
                    )
                )
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            when (selectedTab) {
                "Dashboard" -> DashboardView(okHttpClient)
                "Cart" -> CartView()
                "Profile" -> ProfileView()
            }
        }
    }
}

@Composable
fun DashboardView(okHttpClient: OkHttpClient) {
    val scrollState = rememberScrollState()
    var frictionTextInput by remember { mutableStateOf("") }
    var networkStatusText by remember { mutableStateOf("Idle") }
    var nestedOnboardingStep by remember { mutableStateOf(1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App header
        Text(
            text = "OpenEngage SDK Test Bench",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // SECTION 1: Touch Interaction Friction
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("1. Gestural Friction Testing", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                
                // Dead Tap target 1 (non-clickable text view)
                Text(
                    text = "This Text represents a false button. Click here to trigger a Dead Tap event.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFF5722).copy(alpha = 0.2f))
                        .padding(12.dp)
                        .testTag("dead_tap_text_label"),
                    textAlign = TextAlign.Center,
                    color = Color(0xFFFF8A65)
                )

                // Dead Tap target 2 (clickable but empty callback)
                Button(
                    onClick = { /* Does nothing intentionally */ },
                    modifier = Modifier.fillMaxWidth().testTag("unresponsive_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF607D8B))
                ) {
                    Text("Frozen Click Handler Button")
                }

                // Rage Tap Target
                Button(
                    onClick = { /* Clicking quickly triggers rage tap logging */ },
                    modifier = Modifier.fillMaxWidth().testTag("rage_tap_spam_button")
                ) {
                    Text("Spam Me Quick (Rage Tap)")
                }
            }
        }

        // SECTION 2: Form & Backspace Friction
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("2. Input Friction Testing", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                
                OutlinedTextField(
                    value = frictionTextInput,
                    onValueChange = { frictionTextInput = it },
                    label = { Text("Promo Code / Text Field") },
                    placeholder = { Text("Type and delete quickly...") },
                    modifier = Modifier.fillMaxWidth().testTag("friction_promo_field"),
                    singleLine = true
                )
                
                Text(
                    "Deleting text rapidly will trigger oe_input_friction logs in Logcat.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }

        // SECTION 3: API & Latency Failures
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("3. API Failure Testing (OkHttp)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                
                Text("Status: $networkStatusText", color = MaterialTheme.colorScheme.primary)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            networkStatusText = "Sending Request..."
                            triggerNetworkCall(okHttpClient, "https://httpbin.org/get") { success, msg ->
                                networkStatusText = if (success) "Success 200" else "Failed: $msg"
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("api_200_btn")
                    ) {
                        Text("200 OK")
                    }

                    Button(
                        onClick = {
                            networkStatusText = "Sending Request..."
                            triggerNetworkCall(okHttpClient, "https://httpbin.org/status/404") { success, msg ->
                                networkStatusText = if (success) "Success 200" else "Error: 404 Not Found"
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("api_404_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                    ) {
                        Text("404 Error")
                    }
                }

                Button(
                    onClick = {
                        networkStatusText = "Sending Request..."
                        // Triggers timeout since we set connectTimeout to 2s and request delay is 5s
                        triggerNetworkCall(okHttpClient, "https://httpbin.org/delay/5") { success, msg ->
                            networkStatusText = if (success) "Success 200" else "Exception: $msg"
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("api_timeout_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Trigger API Timeout (2s limits)")
                }
            }
        }

        // SECTION 4: Nested CompositionLocal Scope flows
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("4. Nested Backstack & Context Scope", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                val scopeScreenName = "Onboarding_Step_$nestedOnboardingStep"
                
                // Wraps the nested flow scope
                CompositionLocalProvider(LocalOpenEngageScreen provides scopeScreenName) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF00ADB5).copy(alpha = 0.1f))
                            .padding(16.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Current Resolved Context: ${LocalOpenEngageScreen.current}",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Button(onClick = { if (nestedOnboardingStep > 1) nestedOnboardingStep-- }) {
                                    Text("Back")
                                }
                                Button(onClick = { if (nestedOnboardingStep < 3) nestedOnboardingStep++ }) {
                                    Text("Next Step")
                                }
                            }
                            
                            Button(
                                onClick = {
                                    // Simulates custom error logged in this nested flow
                                    OpenEngage.logError("onboarding_exception_step_$nestedOnboardingStep")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
                            ) {
                                Text("Log Context Error")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CartView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Shopping Cart View\n(oe_screen_scope = Cart)", textAlign = TextAlign.Center)
    }
}

@Composable
fun ProfileView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("User Profile View\n(oe_screen_scope = Profile)", textAlign = TextAlign.Center)
    }
}

private fun triggerNetworkCall(
    okHttpClient: OkHttpClient,
    url: String,
    onComplete: (Boolean, String) -> Unit
) {
    val request = Request.Builder().url(url).build()
    okHttpClient.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            onComplete(false, e.message ?: "Failed")
        }

        override fun onResponse(call: Call, response: Response) {
            val isSuccess = response.isSuccessful
            response.close()
            onComplete(isSuccess, if (isSuccess) "200" else "${response.code}")
        }
    })
}
