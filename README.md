# OpenEngage Android SDK

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)

OpenEngage is a serverless, open-source user friction and technical anomaly tracking library for Android. It operates as a friction-free wrapper on top of **Firebase Analytics (GA4)**, feeding raw event streams directly to **BigQuery** and generating real-time UX dashboards in **Looker Studio**.

With OpenEngage, product managers, engineers, and marketers can detect *why* users drop off (funnel friction) and correlate drops with gestural anomalies, layout bottlenecks, API latencies, or error logs without hosting proprietary SDK backend servers.

---

## Features

*   **Gestural Friction Tracking**: Auto-detects **Rage Taps** (spamming a button) and **Dead Taps** (tapping unresponsive static layers) across XML and Jetpack Compose.
*   **Navigation & Escaping Anomalies**: Tracks **Circular Navigation Loops** and **Back-Button Spam** (repeated back presses indicating a user is trapped).
*   **Form & Input Friction**: Monitors **Backspace Spam** (rapid deletion of $\ge 8$ characters or select-all-delete actions).
*   **Network & Latency Analytics**: Captures status codes $\ge 400$, timeouts, and network connection dropouts via a drop-in OkHttp Interceptor. Automatically masks URL resource identifiers (PII/IDs) to keep BigQuery tables clean.
*   **Compose Nested Flow Scope**: Utilizes Compose `CompositionLocal` and custom trackers for Navigation 2.x/3 to resolve layout hierarchies and flow names at coordinates.
*   **Zero-Overhead ProGuard Protection**: Bundles rules to keep class names of screens and exceptions legible after R8 obfuscation for Looker Studio dashboards.

---

## 1. Quick Integration

### Step 1: Add Repositories
Add the repository (e.g., Maven Central or Jitpack) to your root `settings.gradle.kts` or `build.gradle.kts` file:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

### Step 2: Declare Dependencies
Import only the modules you need. If you do not use OkHttp, you can omit the okhttp module entirely.

```kotlin
dependencies {
    // 1. Core Engine (Includes configuration parsing & event logic)
    implementation("io.github.droidcap:openengage-core:1.0.0")

    // 2. Gesture & Navigation Tracker (For XML Views & Compose)
    implementation("io.github.droidcap:openengage-tracker:1.0.0")

    // 3. OkHttp Diagnostics (Optional: captures API errors & latencies)
    implementation("io.github.droidcap:openengage-okhttp:1.0.0")
}
```

---

## 2. Initialization & Configuration

OpenEngage offers two configuration patterns: **File-based JSON Assets** or **Programmatic Builders**.

### Option A: Assets Configuration (Recommended)
Place an `openengage.json` file inside your app's `src/main/assets/` directory. The SDK automatically reads and parses this configuration at startup.

```json
{
  "rage_tap_count": 3,
  "rage_tap_threshold_ms": 800,
  "dead_tap_threshold_ms": 500,
  "excluded_screens": [
    "LoginActivity",
    "PasswordFragment"
  ],
  "masked_api_endpoints": [
    "/api/v1/auth/.*",
    "/api/v1/users/\\d+/profile"
  ]
}
```

Then initialize OpenEngage in your `Application` class:

```kotlin
class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Starts with auto-merged settings from assets/openengage.json
        OpenEngage.initialize(context = this)
    }
}
```

### Option B: Programmatic Config Builder
If you want to configure parameters dynamically or inject runtime filters in code:

```kotlin
class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val config = OpenEngageConfig.Builder()
            .setRageTapThreshold(taps = 4, timeframeMs = 1000, radiusPx = 120f)
            .setDeadTapThreshold(timeframeMs = 600)
            .excludeScreens(listOf("PaymentGatewayActivity"))
            .addMaskedApiEndpoints(listOf("/api/v2/billing/.*"))
            .setEventFilter { eventName ->
                // Block heavy slow-load events for lower-end devices if desired
                eventName != "oe_slow_load"
            }
            .build()

        OpenEngage.initialize(context = this, config = config)
    }
}
```

---

## 3. UI Screen & Navigation Tracking

OpenEngage tracks screens across standard Views and modern Jetpack Compose layouts.

### A. Traditional XML Views
No manual code is required. OpenEngage registers an `ActivityLifecycleCallbacks` and Fragment lifecycle listener to automatically capture active screens and exits.

### B. Compose Navigation 2.x
Install the routing effect inside your NavHost graph definition:

```kotlin
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    
    // Automatically logs destination changes and updates active screen context
    NavigationViewTrackingEffect(navController)

    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen() }
        composable("cart") { CartScreen() }
    }
}
```

### C. Compose Navigation 3 (Declarative Backstack)
If your app utilizes the Navigation 3 backstack pattern, attach the declarative state tracker:

```kotlin
@Composable
fun MainScreen(backStack: List<ScreenDestination>) {
    // Tracks active screen entry and exit durations automatically
    OpenEngageNavigation3Tracker(backStack = backStack)
    
    // Render destinations...
}
```

### D. Multi-Backstack / Bottom Navigation Orchestration
If your app utilizes bottom tabs containing independent nested stacks, call the multi-backstack coordinator:

```kotlin
@Composable
fun DashboardLayout(selectedTab: String, activeTabBackStack: List<Destination>) {
    // Triggers logs only when the active tab or top item of the active tab switches
    OpenEngageMultiNavigation3Tracker(
        activeTabId = selectedTab,
        activeBackStack = activeTabBackStack
    )
}
```

### E. Nested CompositionLocal Flows (Scoped Screens)
To contextualize friction and custom errors occurring inside sub-screens (e.g. step-by-step wizard dialog, bottom-sheet checkouts):

```kotlin
@Composable
fun CheckoutFlowScreen() {
    val nestedStep = "Step1_Shipping"

    CompositionLocalProvider(
        LocalOpenEngageScreen provides "CheckoutFlow_$nestedStep"
    ) {
        // Any button clicks, rage taps, input fields, or custom errors logged
        // within this block will report screen source as "CheckoutFlow_Step1_Shipping"
        // instead of the parent "CheckoutFlowScreen".
        CheckoutContent()
    }
}
```

---

## 4. Custom Error Logging
If an app catches an exception or handles validation failures, it can log it to OpenEngage to associate it with the active session and screen state:

```kotlin
// 1. Simple error message
OpenEngage.logError("validation_failed_promo_code")

// 2. Exception object with custom parameters
try {
    processPayment()
} catch (e: PaymentException) {
    OpenEngage.logError(
        message = "payment_gateway_rejected",
        throwable = e,
        severity = ErrorSeverity.CRITICAL
    ) {
        putParameter("payment_method", "credit_card")
        putParameter("attempt_number", 3)
    }
}
```

---

## 5. Network Interceptor Setup
If you are using OkHttp, add the `OpenEngageOkHttpInterceptor` to log latency metrics and status errors:

```kotlin
val okHttpClient = OkHttpClient.Builder()
    .addInterceptor(OpenEngageOkHttpInterceptor())
    .build()
```
*Note: Any request that throws an `IOException` (e.g. timeout, DNS resolution failure) or returns status $\ge 400$ is dispatched as an `oe_api_error` event.*

---

## 6. Dependency Conflicts & Version Resolution

To guarantee a friction-free integration, OpenEngage adopts specific dependency compilation scopes. Here is how version mismatches are handled:

### Firebase Analytics & BOM
OpenEngage compiles against the Firebase SDK in **`compileOnly`** scope. The SDK does not include GMS services or config keys natively. 
*   **Resolution**: The SDK will compile using the host application's resolved Firebase BOM version.
*   **Requirement**: You must declare Firebase Analytics in your app module's dependencies. If your app lacks Firebase configurations, the SDK outputs an explanatory warning in Logcat and gracefully ceases reporting instead of throwing runtime exceptions.

### OkHttp Version Conflicts
If the host application uses a different version of OkHttp (e.g. `4.9.x` vs. OpenEngage's `4.12.0`):
*   **Default Behavior**: Gradle automatically upgrades the transitively resolved version to the higher version (`4.12.0`).
*   **Exclusion Option**: If your app must run a lower version and wants to bypass dependency resolution conflicts, you can explicitly exclude transitive OkHttp packaging:
    ```kotlin
    dependencies {
        implementation("io.github.droidcap:openengage-okhttp:1.0.0") {
            exclude(group = "com.squareup.okhttp3", module = "okhttp")
        }
    }
    ```

---

## 7. R8/ProGuard Obfuscation

OpenEngage automatically embeds its own `consumer-rules.pro` file inside the AAR package. When you build your release APK, R8 merges these rules automatically.

The rules preserve legible class naming for tracked screens and custom exceptions:
```proguard
# 1. Keep legible exception class names (allows tracking custom errors by name in dashboards)
-keepnames class * extends java.lang.Throwable { *; }

# 2. Keep legible screen names (Activities & Fragments used for page-views)
-keepnames class * extends android.app.Activity
-keepnames class * extends androidx.fragment.app.Fragment

# 3. Retain lines and source files for trace analytics
-keepattributes Exceptions,Signature,InnerClasses,SourceFile,LineNumberTable
```

---

## 8. BigQuery SQL Analytics Patterns

Once GA4 stream data is exported to BigQuery, use these template queries in your reporting schemas.

### logical Partitioning View (`v_openengage_events`)
Filter out custom app business events from SDK-registered friction pings to avoid dataset pollution:

```sql
CREATE OR REPLACE VIEW `your-project.analytics_XXXXXXXXX.v_openengage_events` AS
SELECT
  event_date,
  TIMESTAMP_MICROS(event_timestamp) as event_timestamp,
  event_name,
  user_pseudo_id,
  (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'oe_session_id') AS oe_session_id,
  (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'oe_screen_name') AS oe_screen_name,
  (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'oe_target_view_id') AS oe_target_view_id,
  (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'oe_target_view_type') AS oe_target_view_type,
  (SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'oe_tap_count') AS oe_tap_count,
  (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'oe_api_endpoint') AS oe_api_endpoint,
  (SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'oe_latency_ms') AS oe_latency_ms,
  (SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'oe_http_status_code') AS oe_http_status_code,
  (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'oe_error_type') AS oe_error_type
FROM
  `your-project.analytics_XXXXXXXXX.events_*`
WHERE
  event_name LIKE 'oe_%';
```

### Retroactive User Identity Stitching
Maps pre-login (anonymous) user sessions with post-login custom identifiers:

```sql
WITH identity_map AS (
  SELECT DISTINCT
    user_pseudo_id,
    LAST_VALUE(user_id IGNORE NULLS) OVER (
      PARTITION BY user_pseudo_id
      ORDER BY event_timestamp
      ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
    ) AS stitched_user_id
  FROM
    `your-project.analytics_XXXXXXXXX.events_*`
)
SELECT
  e.event_timestamp,
  e.event_name,
  e.user_pseudo_id,
  COALESCE(m.stitched_user_id, e.user_pseudo_id) AS resolved_user_id,
  (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'oe_screen_name') AS screen_name
FROM
  `your-project.analytics_XXXXXXXXX.events_*` e
LEFT JOIN
  identity_map m ON e.user_pseudo_id = m.user_pseudo_id;
```

---

## License

```text
Copyright 2026 OpenEngage Authors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
