package com.ivanlolka.omnistream.chat

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class TwitchChatClient(
    private val listener: Listener
) {

    interface Listener {
        fun onChatMessage(author: String, text: String)
        fun onSystemMessage(text: String)
        fun onConnectionStateChanged(state: ConnectionState)
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var channel: String? = null
    private var connected = false

    fun connect(username: String, accessToken: String, channelName: String) {
        disconnect()
        val normalizedUser = username.trim().lowercase()
        val normalizedChannel = channelName.trim().removePrefix("#").lowercase()
        val normalizedToken = accessToken.trim().removePrefix("oauth:")

        if (normalizedUser.isBlank() || normalizedChannel.isBlank() || normalizedToken.isBlank()) {
            listener.onSystemMessage("Для чата Twitch нужно заполнить username, token и channel.")
            return
        }

        channel = normalizedChannel
        listener.onConnectionStateChanged(ConnectionState.CONNECTING)
        val request = Request.Builder().url(TWITCH_IRC_WS).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                sendRaw("CAP REQ :twitch.tv/tags twitch.tv/commands twitch.tv/membership")
                sendRaw("PASS oauth:$normalizedToken")
                sendRaw("NICK $normalizedUser")
                sendRaw("JOIN #$normalizedChannel")
                connected = true
                listener.onConnectionStateChanged(ConnectionState.CONNECTED)
                listener.onSystemMessage("Подключено к Twitch-чату #$normalizedChannel")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                text.split("\r\n")
                    .filter { it.isNotBlank() }
                    .forEach(::handleLine)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
                listener.onConnectionStateChanged(ConnectionState.DISCONNECTED)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
                listener.onConnectionStateChanged(ConnectionState.DISCONNECTED)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected = false
                listener.onConnectionStateChanged(ConnectionState.ERROR)
                listener.onSystemMessage("Ошибка Twitch-чата: ${t.message.orEmpty()}")
            }
        })
    }

    fun sendMessage(message: String) {
        val activeChannel = channel
        if (!connected || activeChannel.isNullOrBlank()) {
            listener.onSystemMessage("Сначала подключите Twitch-чат.")
            return
        }
        if (message.isBlank()) return
        sendRaw("PRIVMSG #$activeChannel :$message")
    }

    fun disconnect() {
        webSocket?.close(1000, "manual_disconnect")
        webSocket = null
        connected = false
        listener.onConnectionStateChanged(ConnectionState.DISCONNECTED)
    }

    private fun handleLine(line: String) {
        if (line.startsWith("PING")) {
            sendRaw(line.replace("PING", "PONG"))
            return
        }

        val privMatch = PRIVMSG_REGEX.find(line)
        if (privMatch != null) {
            val author = privMatch.groupValues[1]
            val text = privMatch.groupValues[3]
            listener.onChatMessage(author, text)
            return
        }

        val joinMatch = JOIN_REGEX.find(line)
        if (joinMatch != null) {
            listener.onSystemMessage("${joinMatch.groupValues[1]} вошел(а) в чат")
            return
        }

        val partMatch = PART_REGEX.find(line)
        if (partMatch != null) {
            listener.onSystemMessage("${partMatch.groupValues[1]} вышел(а) из чата")
        }
    }

    private fun sendRaw(command: String) {
        webSocket?.send(command)
    }

    private companion object {
        const val TWITCH_IRC_WS = "wss://irc-ws.chat.twitch.tv:443"
        val PRIVMSG_REGEX = Regex("^(?:@[^ ]+ )?:([^!]+)!\\S+ PRIVMSG #([^ ]+) :(.*)$")
        val JOIN_REGEX = Regex("^(?:@[^ ]+ )?:([^!]+)!\\S+ JOIN #([^ ]+)$")
        val PART_REGEX = Regex("^(?:@[^ ]+ )?:([^!]+)!\\S+ PART #([^ ]+).*$")
    }
}
