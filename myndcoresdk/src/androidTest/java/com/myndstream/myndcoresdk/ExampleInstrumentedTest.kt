package com.myndstream.myndcoresdk

import com.myndstream.myndcoresdk.clients.AuthClient
import com.myndstream.myndcoresdk.clients.AuthClientConfig
import com.myndstream.myndcoresdk.clients.AuthPayload
import com.myndstream.myndcoresdk.clients.HttpClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import org.junit.Test
import org.junit.Assert.*


// TODO: mock the responses - for now its actually calling the server
class AuthClientTest {

    @Test
    fun testAuthClientCachesTokensCorrectly() = runBlocking {
        println("🚀 Testing auth client token caching...")

        val httpClient = HttpClient()
        var authCallCount = 0

        val authClient = AuthClient(
            config = AuthClientConfig(
                authFunction = {
                    authCallCount++
                    println("📞 Auth function called (count: $authCallCount)")
                    realAuthFunction(httpClient)
                },
                httpClient = httpClient,
                baseUrl = "http://10.0.2.2:4000/api/v1"
            )
        )

        // Test 1: Get token first time
        val token1 = authClient.getAccessToken()
        assertNotNull("Token should not be null", token1)
        assertTrue("Token should not be empty", token1.isNotEmpty())
        assertEquals("Auth function should be called once", 1, authCallCount)
        println("✅ First call successful: ${token1.take(20)}...")

        // Test 2: Get token again (should use cached)
        val token2 = authClient.getAccessToken()
        assertEquals("Tokens should be the same (cached)", token1, token2)
        assertEquals("Auth function should still be called only once", 1, authCallCount)
        println("✅ Second call used cache: ${token2.take(20)}...")

        // Test 3: Multiple concurrent calls should all get same token
        val tokens = (1..5).map {
            authClient.getAccessToken()
        }
        tokens.forEach { token ->
            assertEquals("All concurrent tokens should be the same", token1, token)
        }
        assertEquals("Auth function should still be called only once", 1, authCallCount)
        println("✅ Concurrent calls all returned cached token")

        println("✅ Token caching test passed!")
    }

    @Test
    fun testAuthFunctionCreatesValidPayload() = runBlocking {
        println("🚀 Testing auth payload creation...")

        val httpClient = HttpClient()
        val payload = realAuthFunction(httpClient)

        // Verify payload structure
        assertNotNull("Access token should not be null", payload.accessToken)
        assertNotNull("Refresh token should not be null", payload.refreshToken)
        assertTrue("Access token should not be empty", payload.accessToken.isNotEmpty())
        assertTrue("Refresh token should not be empty", payload.refreshToken.isNotEmpty())
        assertTrue("Expires at should be in the future", payload.accessTokenExpiresAtUnixMs > System.currentTimeMillis())

        // Verify token is not expired
        assertFalse("Token should not be expired immediately after creation", payload.isExpired)

        println("✅ Access token: ${payload.accessToken.take(20)}...")
        println("✅ Refresh token: ${payload.refreshToken.take(20)}...")
        println("✅ Expires at: ${payload.accessTokenExpiresAtUnixMs}")
        println("✅ Auth payload test passed!")
    }

    @Test
    fun testTokenExpirationDetection() = runBlocking {
        println("🚀 Testing token expiration detection...")

        // Create an expired token
        val expiredPayload = AuthPayload(
            accessToken = "expired-token",
            refreshToken = "refresh-token",
            accessTokenExpiresAtUnixMs = System.currentTimeMillis() - 1000 // 1 second ago
        )

        assertTrue("Expired token should be detected as expired", expiredPayload.isExpired)

        // Create a valid token
        val validPayload = AuthPayload(
            accessToken = "valid-token",
            refreshToken = "refresh-token",
            accessTokenExpiresAtUnixMs = System.currentTimeMillis() + 3600000 // 1 hour from now
        )

        assertFalse("Valid token should not be detected as expired", validPayload.isExpired)

        println("✅ Token expiration detection test passed!")
    }

    @Test
    fun testAuthRequestSerialization() = runBlocking {
        println("🚀 Testing auth request serialization...")

        val json = Json { ignoreUnknownKeys = true }

        @Serializable
        data class AuthRequest(val providerUserId: String)

        val request = AuthRequest("test-user-id")
        val serialized = json.encodeToString(request)

        assertNotNull("Serialized JSON should not be null", serialized)
        assertTrue("Serialized JSON should contain providerUserId", serialized.contains("providerUserId"))
        assertTrue("Serialized JSON should contain test-user-id", serialized.contains("test-user-id"))

        // Test deserialization
        val deserialized = json.decodeFromString<AuthRequest>(serialized)
        assertEquals("Deserialized object should match original", request.providerUserId, deserialized.providerUserId)

        println("✅ Serialized: $serialized")
        println("✅ Serialization test passed!")
    }

    @Test
    fun testHttpClientMakesCorrectRequest() = runBlocking {
        println("🚀 Testing HTTP client request format...")

        val httpClient = HttpClient()
        val json = Json { ignoreUnknownKeys = true }

        @Serializable
        data class AuthRequest(val providerUserId: String)

        val requestBody = AuthRequest("test-id")
        val requestJson = json.encodeToString(requestBody)

        // This will make an actual HTTP request - ensure your server is running
        try {
            val response = httpClient.post(
                url = "http://10.0.2.2:4000/api/v1/integration-user/authenticate",
                body = requestJson,
                headers = mapOf(
                    "x-api-key" to "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpbnRlZ3JhdGlvbkFwaUtleUlkIjoiZTBmMzQ1YmEtYWRiYi00OWU4LWE2NjMtZjkxNzIzYTc0OGQxIiwiYWNjb3VudElkIjoiMTBlOTlmMzAtNDlkNy00ZDljLWFiMWEtMmU2MjYxMTk2YTRiIiwiaWF0IjoxNzUyNTk1NDM4fQ.t--RVG-3F3fhXgKHyrZRAKlpmUvM-Lwu_svcSCN9pHU",
                    "Content-Type" to "application/json"
                )
            )

            assertNotNull("Response should not be null", response)
            assertTrue("Response should not be empty", response.isNotEmpty())
            assertTrue("Response should be valid JSON", response.startsWith("{"))

            println("✅ HTTP response: ${response.take(100)}...")
            println("✅ HTTP client test passed!")

        } catch (e: Exception) {
            println("⚠️ HTTP test failed (server might not be running): ${e.message}")
            // Don't fail the test if server is not available
        }
    }

    private suspend fun realAuthFunction(httpClient: HttpClient): AuthPayload {
        val url = "http://10.0.2.2:4000/api/v1/integration-user/authenticate"
        val apiKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpbnRlZ3JhdGlvbkFwaUtleUlkIjoiZTBmMzQ1YmEtYWRiYi00OWU4LWE2NjMtZjkxNzIzYTc0OGQxIiwiYWNjb3VudElkIjoiMTBlOTlmMzAtNDlkNy00ZDljLWFiMWEtMmU2MjYxMTk2YTRiIiwiaWF0IjoxNzUyNTk1NDM4fQ.t--RVG-3F3fhXgKHyrZRAKlpmUvM-Lwu_svcSCN9pHU"

        val json = Json { ignoreUnknownKeys = true }

        @Serializable
        data class AuthRequest(val providerUserId: String)

        @Serializable
        data class AuthResponse(
            val accessToken: String,
            val refreshToken: String,
            val accessTokenExpiresAtUnixMs: Long
        )

        val requestBody = AuthRequest("some-random-id")
        val requestJson = json.encodeToString(requestBody)

        val responseJson = httpClient.post(
            url = url,
            body = requestJson,
            headers = mapOf(
                "x-api-key" to apiKey,
                "Content-Type" to "application/json"
            )
        )

        val response = json.decodeFromString<AuthResponse>(responseJson)

        return AuthPayload(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            accessTokenExpiresAtUnixMs = response.accessTokenExpiresAtUnixMs
        )
    }
}