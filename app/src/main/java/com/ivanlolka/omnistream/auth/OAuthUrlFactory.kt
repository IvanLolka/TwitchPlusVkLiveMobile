package com.ivanlolka.omnistream.auth

import okhttp3.HttpUrl.Companion.toHttpUrl

object OAuthUrlFactory {

    private const val REDIRECT_BASE = "omnistream://oauth"
    private const val TWITCH_SCOPE = "chat:read chat:edit user:read:follows moderation:read"
    private const val VK_SCOPE = "video,offline"
    private const val VK_API_VERSION = "5.199"

    fun twitchRedirectUri(): String = "$REDIRECT_BASE/twitch"

    fun vkRedirectUri(): String = "$REDIRECT_BASE/vk"

    fun buildTwitchAuthorizeUrl(clientId: String, state: String, redirectUri: String): String {
        return "https://id.twitch.tv/oauth2/authorize".toHttpUrl().newBuilder()
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("redirect_uri", redirectUri)
            .addQueryParameter("response_type", "token")
            .addQueryParameter("scope", TWITCH_SCOPE)
            .addQueryParameter("force_verify", "true")
            .addQueryParameter("state", state)
            .build()
            .toString()
    }

    fun buildVkAuthorizeUrl(clientId: String, state: String): String {
        return "https://oauth.vk.com/authorize".toHttpUrl().newBuilder()
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("display", "mobile")
            .addQueryParameter("redirect_uri", vkRedirectUri())
            .addQueryParameter("scope", VK_SCOPE)
            .addQueryParameter("response_type", "token")
            .addQueryParameter("v", VK_API_VERSION)
            .addQueryParameter("state", state)
            .build()
            .toString()
    }
}
