package com.myndstream.myndcoresdk.clients

class AuthedHttpClient(
    private val authClient: AuthClient,
    private val client: IHttpClient
) : IHttpClient {

    constructor(
        authClient: AuthClient,
        config: HttpClientConfig = HttpClientConfig()
    ) : this(authClient, HttpClient(config))

    override suspend fun post(url: String, body: String, headers: Map<String, String>): String {
        val token = authClient.getAccessToken()
        val combinedHeaders = headers + ("Authorization" to "Bearer $token")

        return client.post(
            url = url,
            body = body,
            headers = combinedHeaders
        )
    }

    override suspend fun get(url: String, headers: Map<String, String>): String {
        val token = authClient.getAccessToken()
        val combinedHeaders = headers + ("Authorization" to "Bearer $token")

        return client.get(
            url = url,
            headers = combinedHeaders
        )
    }
}