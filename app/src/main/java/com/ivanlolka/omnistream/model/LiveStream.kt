package com.ivanlolka.omnistream.model

data class LiveStream(
    val platform: Platform,
    val id: String,
    val displayName: String,
    val channelName: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val watchUrl: String? = null,
    val vkOwnerId: String? = null,
    val vkVideoId: String? = null,
    val vkAccessKey: String? = null
)
