package com.ivanlolka.omnistream.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.ivanlolka.omnistream.MainViewModel
import com.ivanlolka.omnistream.R
import com.ivanlolka.omnistream.TwitchOAuthPreview
import com.ivanlolka.omnistream.auth.OAuthLauncher
import com.ivanlolka.omnistream.auth.OAuthUrlFactory
import kotlinx.coroutines.launch

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val viewModel: MainViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val twitchClientIdText = view.findViewById<TextView>(R.id.twitchClientIdText)
        val twitchRedirectText = view.findViewById<TextView>(R.id.twitchRedirectText)
        val vkRedirectText = view.findViewById<TextView>(R.id.vkRedirectText)
        val sessionStateText = view.findViewById<TextView>(R.id.sessionStateText)
        val logoutTwitchButton = view.findViewById<MaterialButton>(R.id.logoutTwitchButton)
        val logoutVkButton = view.findViewById<MaterialButton>(R.id.logoutVkButton)
        val twitchOAuthDebugButton = view.findViewById<MaterialButton>(R.id.twitchOAuthDebugButton)

        twitchRedirectText.text = "Twitch redirect: ${OAuthUrlFactory.twitchRedirectUri()}"
        vkRedirectText.text = "VK redirect: ${OAuthUrlFactory.vkRedirectUri()}"
        twitchClientIdText.text = "Twitch Client ID: ${viewModel.authState.value.twitchClientId}"

        logoutTwitchButton.setOnClickListener { viewModel.clearTwitchAuth() }
        logoutVkButton.setOnClickListener { viewModel.clearVkAuth() }
        twitchOAuthDebugButton.setOnClickListener {
            viewModel.buildTwitchOAuthPreview()
                .onSuccess(::showTwitchOAuthDebugDialog)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.authState.collect { auth ->
                        twitchClientIdText.text = "Twitch Client ID: ${auth.twitchClientId}"

                        twitchRedirectText.text = "Twitch redirect (active): ${auth.twitchRedirectUrl}"
                        val twitchState = if (auth.isTwitchAuthorized) "authorized" else "not authorized"
                        val vkState = if (auth.isVkAuthorized) "authorized" else "not authorized"
                        sessionStateText.text = "Twitch: $twitchState | VK: $vkState"
                    }
                }
            }
        }
    }

    private fun showTwitchOAuthDebugDialog(preview: TwitchOAuthPreview) {
        val message = buildString {
            appendLine("Client ID: ${preview.clientId}")
            appendLine("Redirect URI: ${preview.redirectUri}")
            appendLine("Response type: ${preview.responseType}")
            appendLine("Scope: ${preview.scope}")
            appendLine()
            append("Authorize URL:\n${preview.authUrl}")
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.twitch_oauth_dialog_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.open_in_browser)) { _, _ ->
                OAuthLauncher.launch(requireContext(), preview.authUrl)
            }
            .setNeutralButton(getString(R.string.copy_link)) { _, _ ->
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Twitch OAuth URL", preview.authUrl))
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
