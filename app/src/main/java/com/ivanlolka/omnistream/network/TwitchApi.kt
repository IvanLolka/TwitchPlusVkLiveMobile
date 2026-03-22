package com.ivanlolka.omnistream.network

import com.ivanlolka.omnistream.model.AuthState
import com.ivanlolka.omnistream.model.LiveStream
import com.ivanlolka.omnistream.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
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
                                watchUrl = "https://www.twitch.tv/$channel",
                                twitchBroadcasterId = item.optString("user_id")
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

    suspend fun fetchUserIdByLogin(authState: AuthState, login: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = "https://api.twitch.tv/helix/users".toHttpUrl().newBuilder()
                .addQueryParameter("login", login)
                .build()
                .toString()
            val payload = executeHelixRequest(endpoint, authState)
            val data = payload.optJSONArray("data")
            if (data == null || data.length() == 0) error("Twitch user not found: $login")
            data.getJSONObject(0).optString("id")
        }
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

    suspend fun fetchGlobalBadges(authState: AuthState): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = executeHelixRequest(
                endpoint = "https://api.twitch.tv/helix/chat/badges/global",
                authState = authState
            )
            parseBadgeMap(response.optJSONArray("data"))
        }
    }

    suspend fun fetchChannelBadges(authState: AuthState, broadcasterId: String): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = "https://api.twitch.tv/helix/chat/badges".toHttpUrl()
                .newBuilder()
                .addQueryParameter("broadcaster_id", broadcasterId)
                .build()
                .toString()
            val response = executeHelixRequest(endpoint, authState)
            parseBadgeMap(response.optJSONArray("data"))
        }
    }

    suspend fun fetchGlobalEmotes(authState: AuthState): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            fetchPaginatedEmotes(
                baseUrl = "https://api.twitch.tv/helix/chat/emotes/global",
                authState = authState
            )
        }
    }

    suspend fun fetchChannelEmotes(authState: AuthState, broadcasterId: String): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            fetchPaginatedEmotes(
                baseUrl = "https://api.twitch.tv/helix/chat/emotes",
                authState = authState,
                query = mapOf("broadcaster_id" to broadcasterId)
            )
        }
    }

    suspend fun fetchUserEmotes(
        authState: AuthState,
        broadcasterId: String
    ): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = authState.twitchUserId
            if (userId.isBlank()) return@runCatching emptyMap()
            fetchPaginatedEmotes(
                baseUrl = "https://api.twitch.tv/helix/chat/emotes/user",
                authState = authState,
                query = mapOf(
                    "user_id" to userId,
                    "broadcaster_id" to broadcasterId
                )
            )
        }
    }

    suspend fun fetchEmotesBySetIds(
        authState: AuthState,
        emoteSetIds: List<String>
    ): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val ids = emoteSetIds.filter { it.isNotBlank() }.distinct()
            if (ids.isEmpty()) return@runCatching emptyMap()

            val result = LinkedHashMap<String, String>()
            ids.chunked(MAX_SET_IDS_PER_REQUEST).forEach { chunk ->
                val builder = "https://api.twitch.tv/helix/chat/emotes/set".toHttpUrl().newBuilder()
                chunk.forEach { id -> builder.addQueryParameter("emote_set_id", id) }
                val payload = executeHelixRequest(builder.build().toString(), authState)
                result.putAll(parseEmoteMap(payload.optJSONArray("data")))
            }
            result
        }
    }

    private fun executeHelixRequest(endpoint: String, authState: AuthState): JSONObject {
        val request = Request.Builder()
            .url(endpoint)
            .header("Client-Id", authState.twitchClientId)
            .header("Authorization", "Bearer ${authState.twitchAccessToken}")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Twitch API error ${response.code} at $endpoint")
            }
            return JSONObject(response.body?.string().orEmpty())
        }
    }

    private fun parseBadgeMap(data: JSONArray?): Map<String, String> {
        if (data == null) return emptyMap()
        val result = LinkedHashMap<String, String>()
        for (i in 0 until data.length()) {
            val setItem = data.optJSONObject(i) ?: continue
            val setId = setItem.optString("set_id")
            val versions = setItem.optJSONArray("versions") ?: continue
            for (j in 0 until versions.length()) {
                val version = versions.optJSONObject(j) ?: continue
                val versionId = version.optString("id")
                val url = version.optString("image_url_2x")
                    .ifBlank { version.optString("image_url_1x") }
                if (setId.isNotBlank() && versionId.isNotBlank() && url.isNotBlank()) {
                    result["$setId/$versionId"] = url
                }
            }
        }
        return result
    }

    private fun parseEmoteMap(data: JSONArray?): Map<String, String> {
        if (data == null) return emptyMap()
        val result = LinkedHashMap<String, String>()
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val id = item.optString("id")
            val code = item.optString("name")
            if (id.isBlank() || code.isBlank()) continue
            result[code] = "https://static-cdn.jtvnw.net/emoticons/v2/$id/default/dark/2.0"
        }
        return result
    }

    private fun fetchPaginatedEmotes(
        baseUrl: String,
        authState: AuthState,
        query: Map<String, String> = emptyMap()
    ): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        var cursor: String? = null
        repeat(MAX_PAGINATION_PAGES) {
            val builder = baseUrl.toHttpUrl().newBuilder()
                .addQueryParameter("first", PAGE_SIZE.toString())
            query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
            if (!cursor.isNullOrBlank()) builder.addQueryParameter("after", cursor)

            val payload = executeHelixRequest(builder.build().toString(), authState)
            result.putAll(parseEmoteMap(payload.optJSONArray("data")))
            cursor = payload.optJSONObject("pagination")?.optString("cursor").orEmpty()
            if (cursor.isNullOrBlank()) return result
        }
        return result
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGINATION_PAGES = 25
        const val MAX_SET_IDS_PER_REQUEST = 25
    }
}
