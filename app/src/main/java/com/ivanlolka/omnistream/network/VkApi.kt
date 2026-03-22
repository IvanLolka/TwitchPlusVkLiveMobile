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

class VkApi(
    private val client: OkHttpClient = OkHttpClient()
) {

    suspend fun fetchLiveSubscriptions(authState: AuthState): Result<List<LiveStream>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.vk.com/method/video.getSubscriptions".toHttpUrl().newBuilder()
                .addQueryParameter("extended", "1")
                .addQueryParameter("count", "100")
                .addQueryParameter("v", API_VERSION)
                .addQueryParameter("access_token", authState.vkAccessToken)
                .build()

            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("VK API error ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                val root = JSONObject(body)
                val vkError = root.optJSONObject("error")
                if (vkError != null) {
                    error("VK API error: ${vkError.optString("error_msg")}")
                }

                val items = root.optJSONObject("response")
                    ?.optJSONArray("items")
                    ?: return@use emptyList()

                val result = mutableListOf<LiveStream>()
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    if (!isLikelyLive(item)) continue

                    val ownerId = item.optString("owner_id")
                    val videoId = item.optString("id")
                    val accessKey = item.optString("access_key").takeIf { it.isNotBlank() }
                    val title = item.optString("title", "VK Live")
                    val displayName = item.optString("description")
                        .takeIf { it.isNotBlank() }
                        ?: "VK owner $ownerId"
                    val playerUrl = item.optString("player").takeIf { it.isNotBlank() }
                    val fallbackUrl = buildVkWatchUrl(ownerId, videoId, accessKey)
                    val thumbnailUrl = item.optString("image").takeIf { it.isNotBlank() }

                    result += LiveStream(
                        platform = Platform.VK,
                        id = "$ownerId:$videoId",
                        displayName = displayName,
                        channelName = ownerId,
                        title = title,
                        thumbnailUrl = thumbnailUrl,
                        watchUrl = playerUrl ?: fallbackUrl,
                        vkOwnerId = ownerId,
                        vkVideoId = videoId,
                        vkAccessKey = accessKey
                    )
                }
                result
            }
        }
    }

    suspend fun sendVideoComment(
        authState: AuthState,
        stream: LiveStream,
        message: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val ownerId = stream.vkOwnerId ?: error("Нет owner_id для VK стрима")
            val videoId = stream.vkVideoId ?: error("Нет video_id для VK стрима")

            val urlBuilder = "https://api.vk.com/method/video.createComment".toHttpUrl().newBuilder()
                .addQueryParameter("owner_id", ownerId)
                .addQueryParameter("video_id", videoId)
                .addQueryParameter("message", message)
                .addQueryParameter("v", API_VERSION)
                .addQueryParameter("access_token", authState.vkAccessToken)

            stream.vkAccessKey?.takeIf { it.isNotBlank() }?.let {
                urlBuilder.addQueryParameter("access_key", it)
            }

            val request = Request.Builder().url(urlBuilder.build()).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("VK send error ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                val root = JSONObject(body)
                val vkError = root.optJSONObject("error")
                if (vkError != null) {
                    error("VK send error: ${vkError.optString("error_msg")}")
                }
            }
        }
    }

    private fun isLikelyLive(item: JSONObject): Boolean {
        val liveStatus = item.optString("live_status")
        val live = item.optInt("live", 0) == 1 || item.optInt("is_live", 0) == 1
        return live || liveStatus.contains("live", ignoreCase = true)
    }

    private fun buildVkWatchUrl(ownerId: String, videoId: String, accessKey: String?): String {
        return if (accessKey.isNullOrBlank()) {
            "https://vk.com/video${ownerId}_$videoId"
        } else {
            "https://vk.com/video${ownerId}_$videoId?access_key=$accessKey"
        }
    }

    private companion object {
        const val API_VERSION = "5.199"
    }
}
