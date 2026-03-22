package com.ivanlolka.omnistream

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ivanlolka.omnistream.auth.OAuthProvider
import com.ivanlolka.omnistream.auth.OAuthRequest
import com.ivanlolka.omnistream.auth.OAuthUrlFactory
import com.ivanlolka.omnistream.chat.TwitchChatClient
import com.ivanlolka.omnistream.data.AppStorage
import com.ivanlolka.omnistream.model.AuthState
import com.ivanlolka.omnistream.model.LiveStream
import com.ivanlolka.omnistream.model.Platform
import com.ivanlolka.omnistream.model.UnifiedChatMessage
import com.ivanlolka.omnistream.network.TwitchApi
import com.ivanlolka.omnistream.network.VkApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application), TwitchChatClient.Listener {

    private val storage = AppStorage(application)
    private val httpClient = OkHttpClient.Builder().build()
    private val twitchApi = TwitchApi(httpClient)
    private val vkApi = VkApi(httpClient)
    private val twitchChatClient = TwitchChatClient(this)

    private var currentTwitchConnectionKey: String? = null

    private val _authState = MutableStateFlow(storage.readAuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _liveStreams = MutableStateFlow<List<LiveStream>>(emptyList())
    val liveStreams: StateFlow<List<LiveStream>> = _liveStreams.asStateFlow()

    private val _selectedTwitchStream = MutableStateFlow<LiveStream?>(null)
    val selectedTwitchStream: StateFlow<LiveStream?> = _selectedTwitchStream.asStateFlow()

    private val _selectedVkStream = MutableStateFlow<LiveStream?>(null)
    val selectedVkStream: StateFlow<LiveStream?> = _selectedVkStream.asStateFlow()

    private val _activePlatform = MutableStateFlow(Platform.TWITCH)
    val activePlatform: StateFlow<Platform> = _activePlatform.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<UnifiedChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<UnifiedChatMessage>> = _chatMessages.asStateFlow()

    private val _twitchConnectionState = MutableStateFlow(TwitchChatClient.ConnectionState.DISCONNECTED)
    val twitchConnectionLabel: StateFlow<String> = _twitchConnectionState
        .map { state ->
            when (state) {
                TwitchChatClient.ConnectionState.DISCONNECTED -> "Disconnected"
                TwitchChatClient.ConnectionState.CONNECTING -> "Connecting..."
                TwitchChatClient.ConnectionState.CONNECTED -> "Connected"
                TwitchChatClient.ConnectionState.ERROR -> "Error"
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Disconnected")

    val chatTargetLabel: StateFlow<String> = combine(
        _authState,
        _selectedTwitchStream,
        _selectedVkStream
    ) { auth, twitch, vk ->
        val targets = buildList {
            if (auth.isTwitchAuthorized && twitch != null) add("Twitch")
            if (auth.isVkAuthorized && vk != null) add("VK")
        }
        if (targets.isEmpty()) "No active targets" else targets.joinToString(" + ")
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "No active targets")

    init {
        addSystemMessage("Настройте Twitch/VK и запустите сканирование подписок.")
    }

    fun clearTwitchAuth() {
        val updated = _authState.value.copy(
            twitchUsername = "",
            twitchAccessToken = "",
            twitchUserId = ""
        )
        persistAuthState(updated)
        syncTwitchChatConnection()
    }

    fun clearVkAuth() {
        val updated = _authState.value.copy(
            vkAccessToken = "",
            vkUserId = ""
        )
        persistAuthState(updated)
    }

    fun createTwitchOAuthRequest(): Result<OAuthRequest> {
        val preview = buildTwitchOAuthPreview().getOrElse { return Result.failure(it) }
        storage.savePendingOAuth(OAuthProvider.TWITCH, preview.state)
        return Result.success(
            OAuthRequest(
                provider = OAuthProvider.TWITCH,
                authUrl = preview.authUrl
            )
        )
    }

    fun buildTwitchOAuthPreview(): Result<TwitchOAuthPreview> {
        val clientId = _authState.value.twitchClientId
        if (clientId.isBlank()) {
            return Result.failure(IllegalStateException("Укажите Twitch Client ID в настройках."))
        }
        val state = UUID.randomUUID().toString()
        val redirectUri = _authState.value.twitchRedirectUrl.ifBlank { OAuthUrlFactory.twitchRedirectUri() }
        return Result.success(
            TwitchOAuthPreview(
                clientId = clientId,
                redirectUri = redirectUri,
                responseType = OAuthUrlFactory.twitchResponseType(),
                scope = OAuthUrlFactory.twitchScope(),
                state = state,
                authUrl = OAuthUrlFactory.buildTwitchAuthorizeUrl(clientId, state, redirectUri)
            )
        )
    }

    fun createVkOAuthRequest(): Result<OAuthRequest> {
        val clientId = _authState.value.vkClientId
        if (clientId.isBlank()) {
            return Result.failure(IllegalStateException("Укажите VK Client ID в настройках."))
        }
        val state = UUID.randomUUID().toString()
        storage.savePendingOAuth(OAuthProvider.VK, state)
        return Result.success(
            OAuthRequest(
                provider = OAuthProvider.VK,
                authUrl = OAuthUrlFactory.buildVkAuthorizeUrl(clientId, state)
            )
        )
    }

    fun processOAuthCallback(provider: OAuthProvider, callbackUri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(callbackUri)
                val params = parseCallbackParams(uri)
                val token = params["access_token"]
                    ?: error(params["error_description"] ?: "OAuth token not found.")

                validateOAuthState(provider, params["state"])

                when (provider) {
                    OAuthProvider.TWITCH -> completeTwitchOAuth(token)
                    OAuthProvider.VK -> completeVkOAuth(token, params["user_id"])
                }
                storage.clearPendingOAuth()
            }.onFailure { throwable ->
                addSystemMessage("OAuth error: ${throwable.message.orEmpty()}")
            }
        }
    }

    fun refreshSubscriptions() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            var authSnapshot = _authState.value
            val collected = mutableListOf<LiveStream>()

            if (authSnapshot.isTwitchAuthorized && authSnapshot.twitchUserId.isBlank()) {
                runCatching { twitchApi.fetchCurrentUserId(authSnapshot) }
                    .onSuccess { userId ->
                        authSnapshot = authSnapshot.copy(twitchUserId = userId)
                        persistAuthState(authSnapshot)
                    }
                    .onFailure {
                        addSystemMessage("Не удалось получить Twitch user_id: ${it.message}")
                    }
            }

            if (authSnapshot.isTwitchAuthorized) {
                twitchApi.fetchFollowedLiveStreams(authSnapshot)
                    .onSuccess { collected += it }
                    .onFailure { addSystemMessage("Ошибка Twitch подписок: ${it.message}") }
            }

            if (authSnapshot.isVkAuthorized) {
                vkApi.fetchLiveSubscriptions(authSnapshot)
                    .onSuccess { collected += it }
                    .onFailure { addSystemMessage("Ошибка VK подписок: ${it.message}") }
            }

            _liveStreams.value = collected.sortedBy { it.displayName.lowercase() }
            if (collected.isEmpty()) {
                addSystemMessage("Сейчас нет активных эфиров в ваших подписках.")
            }
            _isRefreshing.value = false
        }
    }

    fun openNativeStream(stream: LiveStream) {
        when (stream.platform) {
            Platform.TWITCH -> _selectedTwitchStream.value = stream
            Platform.VK -> _selectedVkStream.value = stream
        }
        _activePlatform.value = stream.platform
        syncTwitchChatConnection()
    }

    fun pairStreams(twitchStream: LiveStream?, vkStream: LiveStream?, active: Platform) {
        if (twitchStream != null) _selectedTwitchStream.value = twitchStream
        if (vkStream != null) _selectedVkStream.value = vkStream
        _activePlatform.value = active
        syncTwitchChatConnection()
    }

    fun createManualTwitchStream(channel: String): LiveStream? {
        val normalized = channel.trim().removePrefix("#")
        if (normalized.isBlank()) return null
        return LiveStream(
            platform = Platform.TWITCH,
            id = "manual_twitch_$normalized",
            displayName = normalized,
            channelName = normalized,
            title = "Manual Twitch channel",
            watchUrl = "https://www.twitch.tv/$normalized"
        )
    }

    fun createManualVkStream(nickname: String): LiveStream? {
        val normalized = nickname.trim().removePrefix("@")
        if (normalized.isBlank()) return null
        return LiveStream(
            platform = Platform.VK,
            id = "manual_vk_$normalized",
            displayName = "@$normalized",
            channelName = normalized,
            title = "Manual VK channel",
            watchUrl = "https://vk.com/video/@$normalized"
        )
    }

    fun switchActivePlatform(platform: Platform) {
        _activePlatform.value = platform
    }

    fun sendUnifiedMessage(message: String) {
        val text = message.trim()
        if (text.isBlank()) return

        val auth = _authState.value
        val twitchStream = _selectedTwitchStream.value
        val vkStream = _selectedVkStream.value
        val targets = mutableSetOf<Platform>()

        if (auth.isTwitchAuthorized && twitchStream != null) {
            targets += Platform.TWITCH
            twitchChatClient.sendMessage(text)
        }

        if (auth.isVkAuthorized && vkStream != null) {
            targets += Platform.VK
            viewModelScope.launch(Dispatchers.IO) {
                vkApi.sendVideoComment(auth, vkStream, text)
                    .onFailure { addSystemMessage("VK rejected message: ${it.message}") }
            }
        }

        if (targets.isEmpty()) {
            addSystemMessage("Нет авторизованных платформ для отправки.")
            return
        }

        appendChatMessage(
            UnifiedChatMessage(
                author = auth.twitchUsername.ifBlank { "You" },
                text = text,
                targets = targets
            )
        )
    }

    override fun onChatMessage(author: String, text: String) {
        appendChatMessage(
            UnifiedChatMessage(
                author = author,
                text = text,
                targets = setOf(Platform.TWITCH)
            )
        )
    }

    override fun onSystemMessage(text: String) {
        addSystemMessage(text)
    }

    override fun onConnectionStateChanged(state: TwitchChatClient.ConnectionState) {
        _twitchConnectionState.value = state
        if (state == TwitchChatClient.ConnectionState.ERROR) {
            currentTwitchConnectionKey = null
        }
    }

    override fun onCleared() {
        twitchChatClient.disconnect()
        super.onCleared()
    }

    private suspend fun completeTwitchOAuth(accessToken: String) {
        val auth = _authState.value
        val clientId = auth.twitchClientId
        if (clientId.isBlank()) error("Twitch Client ID is empty.")

        val profile = twitchApi.fetchCurrentUserProfile(
            clientId = clientId,
            accessToken = accessToken
        )
        val updated = auth.copy(
            twitchAccessToken = accessToken,
            twitchUserId = profile.id,
            twitchUsername = profile.login
        )
        persistAuthState(updated)
        addSystemMessage("Twitch OAuth completed for ${profile.displayName}.")
        syncTwitchChatConnection()
    }

    private fun completeVkOAuth(accessToken: String, userIdFromCallback: String?) {
        val auth = _authState.value
        val updated = auth.copy(
            vkAccessToken = accessToken,
            vkUserId = userIdFromCallback ?: auth.vkUserId
        )
        persistAuthState(updated)
        addSystemMessage("VK OAuth completed.")
    }

    private fun validateOAuthState(provider: OAuthProvider, callbackState: String?) {
        val (pendingProvider, pendingState) = storage.readPendingOAuth()
        if (pendingProvider != provider) {
            error("OAuth provider mismatch.")
        }
        if (pendingState.isBlank() || callbackState.isNullOrBlank() || callbackState != pendingState) {
            error("OAuth state validation failed.")
        }
    }

    private fun parseCallbackParams(uri: Uri): Map<String, String> {
        val values = mutableMapOf<String, String>()
        uri.queryParameterNames.forEach { key ->
            uri.getQueryParameter(key)?.let { values[key] = it }
        }

        uri.fragment
            ?.split("&")
            ?.mapNotNull { item ->
                val idx = item.indexOf("=")
                if (idx <= 0) return@mapNotNull null
                val key = Uri.decode(item.substring(0, idx))
                val value = Uri.decode(item.substring(idx + 1))
                key to value
            }
            ?.forEach { (key, value) -> values[key] = value }

        return values
    }

    private fun syncTwitchChatConnection() {
        val auth = _authState.value
        val stream = _selectedTwitchStream.value
        val newKey = if (auth.isTwitchAuthorized && stream != null) {
            "${auth.twitchUsername.lowercase()}:${stream.channelName.lowercase()}"
        } else {
            null
        }

        if (newKey == currentTwitchConnectionKey) return
        currentTwitchConnectionKey = newKey

        if (newKey == null) {
            twitchChatClient.disconnect()
            return
        }

        val selectedStream = stream ?: return
        twitchChatClient.connect(
            username = auth.twitchUsername,
            accessToken = auth.twitchAccessToken,
            channelName = selectedStream.channelName
        )
    }

    private fun appendChatMessage(message: UnifiedChatMessage) {
        viewModelScope.launch {
            _chatMessages.update { current ->
                (current + message).takeLast(MAX_CHAT_MESSAGES)
            }
        }
    }

    private fun addSystemMessage(text: String) {
        appendChatMessage(
            UnifiedChatMessage(
                author = "System",
                text = text,
                isSystem = true
            )
        )
    }

    private fun persistAuthState(state: AuthState) {
        _authState.value = state
        storage.saveAuthState(state)
    }

    private companion object {
        const val MAX_CHAT_MESSAGES = 400
    }
}

data class TwitchOAuthPreview(
    val clientId: String,
    val redirectUri: String,
    val responseType: String,
    val scope: String,
    val state: String,
    val authUrl: String
)
