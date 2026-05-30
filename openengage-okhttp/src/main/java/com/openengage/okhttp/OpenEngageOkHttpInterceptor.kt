package com.openengage.okhttp

import com.openengage.core.OpenEngage
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class OpenEngageOkHttpInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()
        val method = request.method
        
        // Notify tracking components of ongoing user activity to reset dead-tap timers
        OpenEngage.notifyUserAction()
        
        val startTime = System.currentTimeMillis()
        val response: Response
        
        try {
            response = chain.proceed(request)
        } catch (e: IOException) {
            val latency = System.currentTimeMillis() - startTime
            val maskedUrl = maskUrl(url)
            val errorType = if (e.message?.contains("time", ignoreCase = true) == true) "TIMEOUT" else "CONNECTION_FAILED"
            
            OpenEngage.logEvent("oe_api_error") {
                putParameter("oe_api_endpoint", maskedUrl)
                putParameter("oe_http_method", method)
                putParameter("oe_latency_ms", latency)
                putParameter("oe_error_type", errorType)
                putParameter("oe_error_message", e.message ?: "Unknown IOException")
            }
            throw e
        }

        val latency = System.currentTimeMillis() - startTime
        
        if (!response.isSuccessful) {
            val maskedUrl = maskUrl(url)
            OpenEngage.logEvent("oe_api_error") {
                putParameter("oe_api_endpoint", maskedUrl)
                putParameter("oe_http_method", method)
                putParameter("oe_latency_ms", latency)
                putParameter("oe_http_status_code", response.code.toLong())
                putParameter("oe_error_type", "HTTP_ERROR")
            }
        }
        
        return response
    }

    private fun maskUrl(url: String): String {
        val config = OpenEngage.getConfig()
        val patterns = config?.maskedApiEndpoints ?: return url
        return com.openengage.core.OpenEngageUrlUtil.maskUrl(url, patterns)
    }
}
