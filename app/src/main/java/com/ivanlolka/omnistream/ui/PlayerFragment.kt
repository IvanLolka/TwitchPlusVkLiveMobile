package com.ivanlolka.omnistream.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ivanlolka.omnistream.MainViewModel
import com.ivanlolka.omnistream.R
import com.ivanlolka.omnistream.model.LiveStream
import com.ivanlolka.omnistream.model.Platform
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class PlayerFragment : Fragment(R.layout.fragment_player) {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var playerWebView: WebView

    private var lastActivePlatform: Platform = Platform.TWITCH
    private var lastTwitchStream: LiveStream? = null
    private var lastVkStream: LiveStream? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playerWebView = view.findViewById(R.id.playerWebView)
        playerWebView.settings.javaScriptEnabled = true
        playerWebView.settings.domStorageEnabled = true
        playerWebView.settings.mediaPlaybackRequiresUserGesture = false
        playerWebView.webChromeClient = WebChromeClient()
        playerWebView.webViewClient = object : WebViewClient() {}

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(
                        viewModel.selectedTwitchStream,
                        viewModel.selectedVkStream,
                        viewModel.activePlatform
                    ) { twitch, vk, active -> Triple(twitch, vk, active) }
                        .collect { (twitch, vk, active) ->
                            lastActivePlatform = active
                            lastTwitchStream = twitch
                            lastVkStream = vk
                            render(active, twitch, vk)
                        }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        playerWebView.onResume()
        render(lastActivePlatform, lastTwitchStream, lastVkStream)
    }

    override fun onPause() {
        playerWebView.onPause()
        super.onPause()
    }

    private fun render(active: Platform, twitch: LiveStream?, vk: LiveStream?) {
        when (active) {
            Platform.TWITCH -> {
                if (twitch == null) {
                    playerWebView.loadData(buildEmptyHtml("Select Twitch stream"), "text/html", "UTF-8")
                    return
                }
                playerWebView.loadDataWithBaseURL(
                    "http://localhost/",
                    buildTwitchEmbedHtml(twitch.channelName),
                    "text/html",
                    "UTF-8",
                    null
                )
            }

            Platform.VK -> {
                if (vk == null) {
                    playerWebView.loadData(buildEmptyHtml("Select VK stream"), "text/html", "UTF-8")
                    return
                }
                val url = vk.watchUrl.orEmpty()
                if (url.isBlank()) {
                    playerWebView.loadData(buildEmptyHtml("VK stream URL is missing"), "text/html", "UTF-8")
                    return
                }
                playerWebView.loadUrl(url)
            }
        }
    }

    private fun buildEmptyHtml(text: String): String {
        return """
            <html>
              <body style="margin:0;background:#0f1218;color:#aab5cc;display:flex;align-items:center;justify-content:center;font-family:Segoe UI,sans-serif;">
                $text
              </body>
            </html>
        """.trimIndent()
    }

    private fun buildTwitchEmbedHtml(channel: String): String {
        return """
            <!DOCTYPE html>
            <html>
              <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                <style>
                  html, body { margin: 0; padding: 0; background: #000; height: 100%; overflow: hidden; }
                  iframe { border: 0; width: 100%; height: 100%; }
                </style>
              </head>
              <body>
                <iframe
                  src="https://player.twitch.tv/?channel=$channel&parent=localhost&autoplay=true&layout=video"
                  allowfullscreen>
                </iframe>
              </body>
            </html>
        """.trimIndent()
    }
}
