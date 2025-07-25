package com.myndstream.myndcoresdk.public

import android.content.Context
import com.myndstream.myndcoresdk.audio.IAudioClient
import com.myndstream.myndcoresdk.exp.MyndAudioClient
import com.myndstream.myndcoresdk.clients.ICatalogueClient
import com.myndstream.myndcoresdk.clients.HttpClient
import com.myndstream.myndcoresdk.clients.AuthClient
import com.myndstream.myndcoresdk.clients.AuthClientConfig
import com.myndstream.myndcoresdk.clients.AuthedHttpClient
import com.myndstream.myndcoresdk.clients.CatalogueClient
import models.AuthPayload

interface IMyndSDK {
    val catalogueClient: ICatalogueClient
    val player: IAudioClient
}

class MyndSDK private constructor(
    override val catalogueClient: ICatalogueClient,
    override val player: IAudioClient
) : IMyndSDK {

    companion object {
        fun create(
            authFunction: suspend () -> AuthPayload,
            ctx: Context
        ): MyndSDK {

            // Create HTTP client
            val httpClient = HttpClient()

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

            return MyndSDK(catalogueClient, player)
        }
    }
}
