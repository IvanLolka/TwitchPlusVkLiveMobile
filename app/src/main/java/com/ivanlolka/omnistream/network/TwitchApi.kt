package com.ivanlolka.omnistream.network

import com.ivanlolka.omnistream.model.AuthState
import com.ivanlolka.omnistream.model.LiveStream
import com.ivanlolka.omnistream.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class TwitchApi(
    private val client: OkHttpClient = OkHttpClient()
) {

    data class UserProfile(
        val id: String,
        val login: String,
        val displayName: String
    )

    suspend fun fetchFollowedLiveStreams(authState: AuthState): Result<List<LiveStream>> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = if (authState.twitchUserId.isBlank()) {
                fetchCurrentUserId(authState)
            } else {
                authState.twitchUserId
            }

            val url = "https://api.twitch.tv/helix/streams/followed".toHttpUrl().newBuilder()
                .addQueryParameter("user_id", userId)
                .addQueryParameter("first", "40")
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Client-Id", authState.twitchClientId)
                .header("Authorization", "Bearer ${authState.twitchAccessToken}")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Twitch API error ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                val data = JSONObject(body).optJSONArray("data") ?: return@use emptyList()
                buildList {
                    for (i in 0 until data.length()) {
                        val item = data.getJSONObject(i)
                        val channel = item.optString("user_login")
                        val thumbnailUrl = item.optString("thumbnail_url").takeIf { it.isNotBlank() }
                        add(
                            LiveStream(
                                platform = Platform.TWITCH,
                                id = item.optString("id"),
                                displayName = item.optString("user_name", channel),
                                channelName = channel,
                                title = item.optString("title", "Untitled stream"),
                                thumbnailUrl = thumbnailUrl,
                                watchUrl = "https://www.twitch.tv/$channel"
                            )
                        )
                    }
                }
            }
        }
    }

    suspend fun fetchCurrentUserId(authState: AuthState): String {
        return fetchCurrentUserProfile(
            clientId = authState.twitchClientId,
            accessToken = authState.twitchAccessToken
        ).id
    }

    suspend fun fetchCurrentUserProfile(clientId: String, accessToken: String): UserProfile = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.twitch.tv/helix/users")
            .header("Client-Id", clientId)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Failed to read Twitch profile: ${response.code}")
            }
            val payload = JSONObject(response.body?.string().orEmpty())
            val data = payload.optJSONArray("data")
            if (data == null || data.length() == 0) {
                error("Empty Twitch profile response.")
            }
            val item = data.getJSONObject(0)
            UserProfile(
                id = item.optString("id"),
                login = item.optString("login"),
                displayName = item.optString("display_name", item.optString("login"))
            )
        }
    }
}
