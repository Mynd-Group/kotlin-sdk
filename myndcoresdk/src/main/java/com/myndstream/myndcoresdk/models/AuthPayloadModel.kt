package models

import core.utils.UnixTimeFormatter
import java.io.Serializable

interface IAuthPayload : Serializable {
    val accessToken: String
    val refreshToken: String
    val accessTokenExpiresAtUnixMs: Int
    val isExpired: Boolean
}

data class AuthPayload(
    override val accessToken: String,
    override val refreshToken: String,
    override val accessTokenExpiresAtUnixMs: Int
) : IAuthPayload {
    override val isExpired: Boolean
        get() = accessTokenExpiresAtUnixMs < UnixTimeFormatter.getCurrentUnixTimeInMS()
}

