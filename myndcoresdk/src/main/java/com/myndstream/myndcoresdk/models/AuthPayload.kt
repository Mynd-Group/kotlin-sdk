package models

import android.annotation.SuppressLint
import core.utils.UnixTimeFormatter

interface IAuthPayload  {
    val accessToken: String
    val refreshToken: String
    val accessTokenExpiresAtUnixMs: Long
    val isExpired: Boolean
}

@SuppressLint("UnsafeOptInUsageError")
@kotlinx.serialization.Serializable
data class AuthPayload(
    override val accessToken: String,
    override val refreshToken: String,
    override val accessTokenExpiresAtUnixMs: Long,
) : IAuthPayload {
    override val isExpired: Boolean
        get() = accessTokenExpiresAtUnixMs < UnixTimeFormatter.getCurrentUnixTimeInMS()
}

