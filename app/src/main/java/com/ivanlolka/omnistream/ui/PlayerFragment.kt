package com.ivanlolka.omnistream.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
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
    private lateinit var infoText: TextView
    private lateinit var toggleGroup: MaterialButtonToggleGroup
    private lateinit var twitchToggle: MaterialButton
    private lateinit var vkToggle: MaterialButton

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playerWebView = view.findViewById(R.id.playerWebView)
        infoText = view.findViewById(R.id.linkInfoText)
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
        val targetButton = if (active == Platform.TWITCH) R.id.twitchToggleButton else R.id.vkToggleButton
        if (toggleGroup.checkedButtonId != targetButton) {
            toggleGroup.check(targetButton)
        }
    }

    private fun render(active: Platform, twitch: LiveStream?, vk: LiveStream?) {
        val twitchLabel = twitch?.displayName ?: "не выбран"
        val vkLabel = vk?.displayName ?: "не выбран"
        infoText.text = "${getString(R.string.linked_channels)}\n" +
            getString(R.string.twitch_channel_label, twitchLabel) + "\n" +
            getString(R.string.vk_channel_label, vkLabel)

        when (active) {
            Platform.TWITCH -> {
                if (twitch == null) {
                    playerWebView.loadData("<html><body>Выберите Twitch-стрим</body></html>", "text/html", "UTF-8")
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
                    playerWebView.loadData("<html><body>Выберите VK-стрим</body></html>", "text/html", "UTF-8")
                    return
                }
                val url = vk.watchUrl.orEmpty()
                if (url.isBlank()) {
                    playerWebView.loadData("<html><body>У VK стрима нет URL для открытия</body></html>", "text/html", "UTF-8")
                    return
                }
                playerWebView.loadUrl(url)
            }
        }
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
