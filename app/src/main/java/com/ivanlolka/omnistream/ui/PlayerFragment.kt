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
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.ivanlolka.omnistream.MainViewModel
import com.ivanlolka.omnistream.R
import com.ivanlolka.omnistream.model.LiveStream
import com.ivanlolka.omnistream.model.Platform
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class PlayerFragment : Fragment(R.layout.fragment_player) {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var playerWebView: WebView
    private lateinit var toggleGroup: MaterialButtonToggleGroup
    private lateinit var twitchToggle: MaterialButton
    private lateinit var vkToggle: MaterialButton

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playerWebView = view.findViewById(R.id.playerWebView)
        toggleGroup = view.findViewById(R.id.platformToggle)
        twitchToggle = view.findViewById(R.id.twitchToggleButton)
        vkToggle = view.findViewById(R.id.vkToggleButton)

        playerWebView.settings.javaScriptEnabled = true
        playerWebView.settings.domStorageEnabled = true
        playerWebView.settings.mediaPlaybackRequiresUserGesture = false
        playerWebView.webChromeClient = WebChromeClient()
        playerWebView.webViewClient = object : WebViewClient() {}

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.twitchToggleButton -> viewModel.switchActivePlatform(Platform.TWITCH)
                R.id.vkToggleButton -> viewModel.switchActivePlatform(Platform.VK)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(
                        viewModel.selectedTwitchStream,
                        viewModel.selectedVkStream,
                        viewModel.activePlatform
                    ) { twitch, vk, active -> Triple(twitch, vk, active) }
                        .collect { (twitch, vk, active) ->
                            syncToggleState(active, twitch, vk)
                            render(active, twitch, vk)
                        }
                }
            }
        }
    }

    private fun syncToggleState(active: Platform, twitch: LiveStream?, vk: LiveStream?) {
        twitchToggle.isEnabled = twitch != null
        vkToggle.isEnabled = vk != null

        val desired = when {
            active == Platform.TWITCH && twitch != null -> R.id.twitchToggleButton
            active == Platform.VK && vk != null -> R.id.vkToggleButton
            twitch != null -> R.id.twitchToggleButton
            vk != null -> R.id.vkToggleButton
            else -> R.id.twitchToggleButton
        }
        if (toggleGroup.checkedButtonId != desired) {
            toggleGroup.check(desired)
        }
    }

    private fun render(active: Platform, twitch: LiveStream?, vk: LiveStream?) {
        when (active) {
            Platform.TWITCH -> {
                if (twitch == null) {
                    playerWebView.loadData(buildEmptyHtml("Выберите Twitch-стрим"), "text/html", "UTF-8")
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
                    playerWebView.loadData(buildEmptyHtml("Выберите VK-стрим"), "text/html", "UTF-8")
                    return
                }
                val url = vk.watchUrl.orEmpty()
                if (url.isBlank()) {
                    playerWebView.loadData(buildEmptyHtml("У VK-стрима нет URL для открытия"), "text/html", "UTF-8")
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
