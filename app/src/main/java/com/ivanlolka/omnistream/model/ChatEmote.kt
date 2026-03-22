package com.ivanlolka.omnistream.model

data class ChatEmote(
    val code: String,
    val imageUrl: String,
    val source: EmoteSource,
    val category: EmoteCategory
)

enum class EmoteSource {
    TWITCH,
    SEVEN_TV
}

enum class EmoteCategory {
    TWITCH_CHANNEL,
    TWITCH_GLOBAL,
    SEVEN_TV_CHANNEL,
    SEVEN_TV_GLOBAL
}
