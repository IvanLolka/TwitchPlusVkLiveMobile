package com.ivanlolka.omnistream.data

import android.content.Context
import com.ivanlolka.omnistream.auth.OAuthProvider
import com.ivanlolka.omnistream.model.AuthState

class AppStorage(context: Context) {

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun readAuthState(): AuthState {
        return AuthState(
            twitchClientId = AuthState().twitchClientId,
            twitchRedirectUrl = preferences.getString(KEY_TWITCH_REDIRECT_URL, "").orEmpty(),
            vkClientId = preferences.getString(KEY_VK_CLIENT_ID, "").orEmpty(),
            twitchUsername = preferences.getString(KEY_TWITCH_USERNAME, "").orEmpty(),
            twitchAccessToken = preferences.getString(KEY_TWITCH_ACCESS_TOKEN, "").orEmpty(),
            twitchUserId = preferences.getString(KEY_TWITCH_USER_ID, "").orEmpty(),
            vkAccessToken = preferences.getString(KEY_VK_ACCESS_TOKEN, "").orEmpty(),
            vkUserId = preferences.getString(KEY_VK_USER_ID, "").orEmpty(),
            vkDefaultNickname = preferences.getString(KEY_VK_DEFAULT_NICKNAME, "").orEmpty()
        )
    }

    fun saveAuthState(state: AuthState) {
        preferences.edit()
            .putString(KEY_TWITCH_REDIRECT_URL, state.twitchRedirectUrl)
            .putString(KEY_VK_CLIENT_ID, state.vkClientId)
            .putString(KEY_TWITCH_USERNAME, state.twitchUsername)
            .putString(KEY_TWITCH_ACCESS_TOKEN, state.twitchAccessToken)
            .putString(KEY_TWITCH_USER_ID, state.twitchUserId)
            .putString(KEY_VK_ACCESS_TOKEN, state.vkAccessToken)
            .putString(KEY_VK_USER_ID, state.vkUserId)
            .putString(KEY_VK_DEFAULT_NICKNAME, state.vkDefaultNickname)
            .apply()
    }

    fun savePendingOAuth(provider: OAuthProvider, state: String) {
        preferences.edit()
            .putString(KEY_PENDING_OAUTH_PROVIDER, provider.name)
            .putString(KEY_PENDING_OAUTH_STATE, state)
            .apply()
    }

    fun readPendingOAuth(): Pair<OAuthProvider?, String> {
        val providerName = preferences.getString(KEY_PENDING_OAUTH_PROVIDER, null)
        val provider = providerName?.let {
            runCatching { OAuthProvider.valueOf(it) }.getOrNull()
        }
        val state = preferences.getString(KEY_PENDING_OAUTH_STATE, "").orEmpty()
        return provider to state
    }

    fun clearPendingOAuth() {
        preferences.edit()
            .remove(KEY_PENDING_OAUTH_PROVIDER)
            .remove(KEY_PENDING_OAUTH_STATE)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "omnistream_settings"
        const val KEY_TWITCH_REDIRECT_URL = "twitch_redirect_url"
        const val KEY_VK_CLIENT_ID = "vk_client_id"
        const val KEY_TWITCH_USERNAME = "twitch_username"
        const val KEY_TWITCH_ACCESS_TOKEN = "twitch_access_token"
        const val KEY_TWITCH_USER_ID = "twitch_user_id"
        const val KEY_VK_ACCESS_TOKEN = "vk_access_token"
        const val KEY_VK_USER_ID = "vk_user_id"
        const val KEY_VK_DEFAULT_NICKNAME = "vk_default_nickname"
        const val KEY_PENDING_OAUTH_PROVIDER = "pending_oauth_provider"
        const val KEY_PENDING_OAUTH_STATE = "pending_oauth_state"
    }
}
