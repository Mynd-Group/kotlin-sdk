package com.myndstream.myndcoresdk.public

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.myndstream.myndcoresdk.playback.IAudioClient
import com.myndstream.myndcoresdk.playback.MyndAudioClient
import com.myndstream.myndcoresdk.clients.ICatalogueClient
import com.myndstream.myndcoresdk.clients.HttpClient
import com.myndstream.myndcoresdk.clients.AuthClient
import com.myndstream.myndcoresdk.clients.AuthClientConfig
import com.myndstream.myndcoresdk.clients.AuthedHttpClient
import com.myndstream.myndcoresdk.clients.CatalogueClient
import com.myndstream.myndcoresdk.clients.HttpClientConfig
import models.AuthPayload

interface IMyndSDK {
    val catalogueClient: ICatalogueClient
    val player: IAudioClient
}

@OptIn(UnstableApi::class)
class MyndSDK private constructor(
    override val catalogueClient: ICatalogueClient,
    override val player: IAudioClient
) : IMyndSDK {

    companion object {
        @Volatile
        private var INSTANCE: MyndSDK? = null

        fun getOrCreate(
            authFunction: suspend () -> AuthPayload,
            ctx: Context
        ): MyndSDK {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    // Create HTTP client with internal config
                    val httpConfig = HttpClientConfig(
                        connectTimeoutMs = 5_000,
                        readTimeoutMs = 5_000,
                        writeTimeoutMs = 5_000,
                        maxRetries = 3,
                        retryDelayMs = 1_000
                    )
                    val httpClient = HttpClient(httpConfig)

                    // Create auth client
                    val authClient = AuthClient(
                        config = AuthClientConfig(
                            authFunction = authFunction,
                            httpClient = httpClient,
                            baseUrl = Config.baseApiUrl
                        )
                    )

                    // Create authed HTTP client
                    val authedHttpClient = AuthedHttpClient(
                        authClient = authClient,
                        client = httpClient
                    )

                    // Create catalogue client
                    val catalogueClient = CatalogueClient(
                        authedHttpClient = authedHttpClient,
                        baseUrl = Config.baseApiUrl
                    )

                    val player = MyndAudioClient.create(ctx)

                    println("MyndSDK initialized as singleton")

                    MyndSDK(catalogueClient, player).also { INSTANCE = it }
                }
            }
        }

        fun release() {
            synchronized(this) {
                INSTANCE?.let { sdk ->
                    // Release player resources
                    (sdk.player as? MyndAudioClient)?.release()
                }
                INSTANCE = null
                println("MyndSDK released")
            }
        }
    }
}
