package com.ivanlolka.omnistream.ui

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

        twitchRedirectText.text = "Twitch redirect: ${OAuthUrlFactory.twitchRedirectUri()}"
        vkRedirectText.text = "VK redirect: ${OAuthUrlFactory.vkRedirectUri()}"
        twitchClientIdText.text = "Twitch Client ID: ${viewModel.authState.value.twitchClientId}"

        logoutTwitchButton.setOnClickListener { viewModel.clearTwitchAuth() }
        logoutVkButton.setOnClickListener { viewModel.clearVkAuth() }

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
}
