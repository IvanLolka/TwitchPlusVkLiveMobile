package com.ivanlolka.omnistream.model

data class AuthState(
    val twitchClientId: String = "hhh9xkjdta2nonleh4y8xyn34p0mfb",
    val twitchRedirectUrl: String = "https://ivanlolka.github.io/TwitchPlusVkLiveMobile/",
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
