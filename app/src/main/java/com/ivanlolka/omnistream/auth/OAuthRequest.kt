package com.ivanlolka.omnistream.auth

data class OAuthRequest(
    val provider: OAuthProvider,
    val authUrl: String
)
