package com.myndstream.myndcoresdk.clients

import android.annotation.SuppressLint
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import models.AuthPayload

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class RefreshRequest(val refreshToken: String)


// Configuration
data class AuthClientConfig(
    val refreshToken: String,
    val httpClient: IHttpClient,
    val baseUrl: String
)

// Errors
sealed class AuthError : Exception() {
    object InvalidRefreshToken : AuthError()
}

// Auth Client
class AuthClient(private val config: AuthClientConfig) {

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()

    private var authPayload: AuthPayload? = null
    private var authPayloadTask: Deferred<AuthPayload>? = null

    suspend fun getAccessToken(): String = mutex.withLock {
        // Handle ongoing task
        authPayloadTask?.let { task ->
            return@withLock handleOutstandingTask(task)
        }

        val currentPayload = authPayload

        return@withLock when {
            currentPayload == null -> handleNoAuthPayload()
            currentPayload.isExpired -> handleExpiredAuthPayload()
            else -> handleValidAuthPayload(currentPayload)
        }
    }

    private suspend fun handleNoAuthPayload(): String {
        println("AuthClient: No auth payload, getting initial access token")

        val task = scope.async {
            println("AuthClient: Getting access token with refresh token")
            val payload = getAccessTokenWithRefreshToken(config.refreshToken)
            println("AuthClient: Got initial access token")
            payload
        }

        authPayloadTask = task

        return try {
            val payload = task.await()
            authPayload = payload
            handleValidAuthPayload(payload)
        } finally {
            authPayloadTask = null
        }
    }

    private suspend fun handleExpiredAuthPayload(): String {
        println("AuthClient: Token is expired, refreshing")

        val task = scope.async {
            refreshAccessToken() ?: run {
                println("AuthClient: Refresh token returned null payload")
                throw AuthError.InvalidRefreshToken
            }
        }

        authPayloadTask = task

        return try {
            val payload = task.await()
            authPayload = payload
            handleValidAuthPayload(payload)
        } finally {
            authPayloadTask = null
        }
    }

    private suspend fun handleOutstandingTask(task: Deferred<AuthPayload>): String {
        println("AuthClient: Outstanding task, waiting for it to finish")

        val payload = task.await()
        authPayload = payload
        println("AuthClient: Task finished, returning token")

        return handleValidAuthPayload(payload)
    }

    private fun handleValidAuthPayload(payload: AuthPayload): String {
        println("AuthClient: Token is not expired, returning token")
        return payload.accessToken
    }

    private suspend fun getAccessTokenWithRefreshToken(refreshToken: String): AuthPayload {
        println("AuthClient: Getting access token with refresh token")

        val url = "${config.baseUrl}/integration-user/refresh-token"

        return try {
            val requestJson = json.encodeToString(RefreshRequest(refreshToken))

            val responseJson = config.httpClient.post(
                url = url,
                body = requestJson,
                headers = mapOf("Content-Type" to "application/json")
            )

            val response = json.decodeFromString<AuthPayload>(responseJson)
            println("AuthClient: Successfully got access token")
            response
        } catch (e: Exception) {
            println("AuthClient: Failed to get access token: ${e.message}")
            throw AuthError.InvalidRefreshToken
        }
    }

    private suspend fun refreshAccessToken(): AuthPayload? {
        println("AuthClient: Refreshing access token")

        val currentPayload = authPayload ?: run {
            println("AuthClient: No current payload")
            throw AuthError.InvalidRefreshToken
        }

        println("AuthClient: Current payload exists")

        val url = "${config.baseUrl}/integration-user/refresh-token"


        return try {
            val requestJson = json.encodeToString(RefreshRequest(currentPayload.refreshToken))

            val responseJson = config.httpClient.post(
                url = url,
                body = requestJson,
                headers = mapOf("Content-Type" to "application/json")
            )

            val response = json.decodeFromString<AuthPayload>(responseJson)
            println("AuthClient: Refresh successful")
            response
        } catch (e: Exception) {
            println("AuthClient: Bad server response: ${e.message}")
            throw AuthError.InvalidRefreshToken
        }
    }
}