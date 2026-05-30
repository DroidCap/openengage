package com.openengage.sample

import android.app.Application
import com.openengage.core.OpenEngage
import com.openengage.core.OpenEngageConfig
import com.openengage.tracker.xml.XmlTracker

class SampleApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize OpenEngage with customized parameters
        val config = OpenEngageConfig.Builder()
            .setRageTapThreshold(taps = 3, timeframeMs = 1000, radiusPx = 150f)
            .setDeadTapThreshold(timeframeMs = 600)
            .addMaskedApiEndpoints(listOf(".*/users/\\d+.*")) // Mask user IDs in OkHttp logging
            .build()

        OpenEngage.initialize(this, config)

        // Install XML Lifecycle and Touch interceptors
        XmlTracker.install(this)
    }
}
