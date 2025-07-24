package com.myndstream.myndcoresdk.clients

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

// Data classes
@Serializable
data class AuthPayload(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAtUnixMs: Long
) {
    val isExpired: Boolean
        get() = accessTokenExpiresAtUnixMs < System.currentTimeMillis()
}

// Configuration
data class AuthClientConfig(
    val authFunction: suspend () -> AuthPayload,
    val httpClient: IHttpClient,
    // TODO: baseUrl should be an internal thing
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
        println("AuthClient: No auth payload, launching auth function")

        val task = scope.async {
            println("AuthClient: Launching auth function")
            val payload = config.authFunction()
            println("AuthClient: Auth function returned payload")
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
            try {
                refreshAccessToken() ?: run {
                    println("AuthClient: Refresh token returned null payload")
                    throw AuthError.InvalidRefreshToken
                }
            } catch (e: Exception) {
                println("AuthClient: Refresh failed, falling back to auth function")
                config.authFunction()
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

    private suspend fun refreshAccessToken(): AuthPayload? {
        println("AuthClient: Refreshing access token")

        val currentPayload = authPayload ?: run {
            println("AuthClient: No current payload")
            throw AuthError.InvalidRefreshToken
        }

        println("AuthClient: Current payload exists")

        val url = "${config.baseUrl}/integration-user/refresh-token"

        @Serializable
        data class RefreshRequest(val refreshToken: String)

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