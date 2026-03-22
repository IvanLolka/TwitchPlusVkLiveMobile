package com.ivanlolka.omnistream.model

data class UnifiedChatMessage(
    val id: Long = System.currentTimeMillis(),
    val author: String,
    val text: String,
    val targets: Set<Platform> = emptySet(),
    val isSystem: Boolean = false
)
