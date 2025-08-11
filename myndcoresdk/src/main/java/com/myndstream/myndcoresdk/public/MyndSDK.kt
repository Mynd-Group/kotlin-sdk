package com.myndstream.myndcoresdk.public

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.myndstream.myndcoresdk.clients.AuthClient
import com.myndstream.myndcoresdk.clients.AuthClientConfig
import com.myndstream.myndcoresdk.clients.AuthedHttpClient
import com.myndstream.myndcoresdk.clients.CatalogueClient
import com.myndstream.myndcoresdk.clients.HttpClient
import com.myndstream.myndcoresdk.clients.HttpClientConfig
import com.myndstream.myndcoresdk.clients.ICatalogueClient
import com.myndstream.myndcoresdk.clients.TrackingClient
import com.myndstream.myndcoresdk.core.utils.ListeningSessionManager
import com.myndstream.myndcoresdk.playback.IAudioClient
import com.myndstream.myndcoresdk.playback.PlaybackClient

interface IMyndSDK {
    val catalogueClient: ICatalogueClient
    val player: IAudioClient

    fun setCurrentMood(mood: Float)
    fun rateListeningSession(rating: Float)
}

@OptIn(UnstableApi::class)
class MyndSDK
private constructor(
        override val catalogueClient: ICatalogueClient,
        override val player: IAudioClient
) : IMyndSDK {

    override fun setCurrentMood(mood: Float) {
        if (mood < 0 || mood > 1) {
            throw IllegalArgumentException("Mood should be between 0 and 1")
        }

        // TODO: implement

        return
    }

    override fun rateListeningSession(rating: Float) {
        if (rating < 0 || rating > 1) {
            throw IllegalArgumentException("Rating should be between 0 and 1")
        }

        // TODO: implement

        return
    }

    companion object {
        @Volatile private var INSTANCE: MyndSDK? = null

        fun getOrCreate(refreshToken: String, ctx: Context): MyndSDK {
            return INSTANCE
                    ?: synchronized(this) {
                        INSTANCE
                                ?: run {
                                    // Create HTTP client with internal config
                                    val httpConfig =
                                            HttpClientConfig(
                                                    connectTimeoutMs = 5_000,
                                                    readTimeoutMs = 5_000,
                                                    writeTimeoutMs = 5_000,
                                                    maxRetries = 3,
                                                    retryDelayMs = 1_000
                                            )
                                    val httpClient = HttpClient(httpConfig)

                                    // Create auth client
                                    val authClient =
                                            AuthClient(
                                                    config =
                                                            AuthClientConfig(
                                                                    refreshToken = refreshToken,
                                                                    httpClient = httpClient,
                                                                    baseUrl = Config.baseApiUrl
                                                            )
                                            )

                                    // Create authed HTTP client
                                    val authedHttpClient =
                                            AuthedHttpClient(
                                                    authClient = authClient,
                                                    client = httpClient
                                            )

                                    // Create catalogue client
                                    val catalogueClient =
                                            CatalogueClient(
                                                    authedHttpClient = authedHttpClient,
                                                    baseUrl = Config.baseApiUrl
                                            )

                                    val trackingClient =
                                            TrackingClient(
                                                    authedHttpClient = authedHttpClient,
                                                    baseUrl = Config.baseApiUrl
                                            )
                                    val sessionManager = ListeningSessionManager()
                                    val player =
                                            PlaybackClient.create(
                                                    ctx = ctx,
                                                    trackingClient = trackingClient,
                                                    sessionManager = sessionManager
                                            )

                                    println("MyndSDK initialized as singleton")

                                    MyndSDK(catalogueClient, player).also { INSTANCE = it }
                                }
                    }
        }

        fun release() {
            synchronized(this) {
                INSTANCE?.let { sdk ->
                    // Release player resources
                    (sdk.player as? PlaybackClient)?.release()
                }
                INSTANCE = null
                println("MyndSDK released")
            }
        }
    }
}
