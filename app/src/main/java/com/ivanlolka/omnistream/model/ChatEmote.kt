package com.ivanlolka.omnistream.model

data class ChatEmote(
    val code: String,
    val imageUrl: String,
    val source: EmoteSource
)

enum class EmoteSource {
    TWITCH,
    SEVEN_TV
}
