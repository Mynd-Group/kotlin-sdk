package com.myndstream.myndcoresdk.clients

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

// Simple HTTP Client Interface
interface IHttpClient {
    suspend fun post(url: String, body: String, headers: Map<String, String>): String
    suspend fun get(url: String, headers: Map<String, String>): String
}

class HttpClient : IHttpClient {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val okHttpClient = OkHttpClient()

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

        return executeRequest(request.build())
    }

    override suspend fun get(url: String, headers: Map<String, String>): String {
        val request = Request.Builder()
            .url(url)
            .get()

        headers.forEach { (key, value) -> request.addHeader(key, value) }

        println("🔵 HTTP GET Request:")
        println("  URL: $url")
        println("  Headers: $headers")

        return executeRequest(request.build())
    }

    private suspend fun executeRequest(request: Request): String {
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