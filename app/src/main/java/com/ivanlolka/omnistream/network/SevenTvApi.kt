package com.ivanlolka.omnistream.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class SevenTvApi(
    private val client: OkHttpClient = OkHttpClient()
) {

    suspend fun fetchGlobalEmotes(): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://7tv.io/v3/emote-sets/global")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("7TV global emotes error ${response.code}")
                }
                val payload = JSONObject(response.body?.string().orEmpty())
                parseEmoteMap(payload.optJSONArray("emotes"))
            }
        }
    }

    suspend fun fetchChannelEmotes(twitchRoomId: String): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://7tv.io/v3/users/twitch/$twitchRoomId")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("7TV channel emotes error ${response.code}")
                }
                val payload = JSONObject(response.body?.string().orEmpty())
                val emoteSet = payload.optJSONObject("emote_set")
                parseEmoteMap(emoteSet?.optJSONArray("emotes"))
            }
        }
    }

    private fun parseEmoteMap(emotes: JSONArray?): Map<String, String> {
        if (emotes == null) return emptyMap()
        val result = LinkedHashMap<String, String>()
        for (i in 0 until emotes.length()) {
            val item = emotes.optJSONObject(i) ?: continue
            val code = item.optString("name")
            val id = item.optString("id")
                .ifBlank { item.optJSONObject("data")?.optString("id").orEmpty() }
            if (code.isBlank() || id.isBlank()) continue
            result[code] = "https://cdn.7tv.app/emote/$id/2x.webp"
        }
        return result
    }
}
