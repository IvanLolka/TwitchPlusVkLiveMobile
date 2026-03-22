package com.ivanlolka.omnistream.model

data class AuthState(
    val twitchClientId: String = "34hs789xbxkuf77lwb8n26sm7dqwkp",
    val twitchRedirectUrl: String = "",
    val vkClientId: String = "",
    val twitchUsername: String = "",
    val twitchAccessToken: String = "",
    val twitchUserId: String = "",
    val vkAccessToken: String = "",
    val vkUserId: String = "",
    val vkDefaultNickname: String = ""
) {
    val isTwitchAuthorized: Boolean
        get() = twitchClientId.isNotBlank() && twitchUsername.isNotBlank() && twitchAccessToken.isNotBlank()

    val isVkAuthorized: Boolean
        get() = vkAccessToken.isNotBlank()
}
