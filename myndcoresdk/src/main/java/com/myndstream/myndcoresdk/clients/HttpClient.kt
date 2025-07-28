package com.myndstream.myndcoresdk.clients

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

// HTTP Client Configuration
data class HttpClientConfig(
    val connectTimeoutMs: Long = 30_000,
    val readTimeoutMs: Long = 30_000,
    val writeTimeoutMs: Long = 30_000,
    val maxRetries: Int = 3,
    val retryDelayMs: Long = 1_000
)

// Simple HTTP Client Interface
interface IHttpClient {
    suspend fun post(url: String, body: String, headers: Map<String, String>): String
    suspend fun get(url: String, headers: Map<String, String>): String
}

class HttpClient(private val config: HttpClientConfig = HttpClientConfig()) : IHttpClient {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(config.connectTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(config.readTimeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(config.writeTimeoutMs, TimeUnit.MILLISECONDS)
        .build()

    override suspend fun post(url: String, body: String, headers: Map<String, String>): String {
        val requestBody = body.toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)

        headers.forEach { (key, value) -> request.addHeader(key, value) }

        println("🔵 HTTP POST Request:")
        println("  URL: $url")
        println("  Headers: $headers")
        println("  Body: $body")

        return executeRequestWithRetry(request.build())
    }

    override suspend fun get(url: String, headers: Map<String, String>): String {
        val request = Request.Builder()
            .url(url)
            .get()

        headers.forEach { (key, value) -> request.addHeader(key, value) }

        println("🔵 HTTP GET Request:")
        println("  URL: $url")
        println("  Headers: $headers")

        return executeRequestWithRetry(request.build())
    }

    private suspend fun executeRequestWithRetry(request: Request): String = withContext(Dispatchers.IO) {
        var lastException: IOException? = null
        
        repeat(config.maxRetries + 1) { attempt ->
            try {
                return@withContext executeRequest(request)
            } catch (e: IOException) {
                lastException = e
                if (attempt < config.maxRetries) {
                    println("🔄 HTTP request failed (attempt ${attempt + 1}/${config.maxRetries + 1}), retrying in ${config.retryDelayMs}ms: ${e.message}")
                    delay(config.retryDelayMs * (attempt + 1)) // Exponential backoff
                }
            }
        }
        
        throw lastException ?: IOException("Request failed after ${config.maxRetries + 1} attempts")
    }

    private fun executeRequest(request: Request): String {
        okHttpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
                ?: throw IOException("Empty response")

            println("🟢 HTTP Response:")
            println("  Status: ${response.code}")
            println("  Body: $responseBody")

            if (!response.isSuccessful) {
                println("❌ HTTP Error: ${response.code}")
                throw IOException("HTTP Error: ${response.code}")
            }

            return responseBody
        }
    }
}