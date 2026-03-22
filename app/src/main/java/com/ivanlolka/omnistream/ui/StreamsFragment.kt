package com.ivanlolka.omnistream.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.ivanlolka.omnistream.MainActivity
import com.ivanlolka.omnistream.MainViewModel
import com.ivanlolka.omnistream.R
import com.ivanlolka.omnistream.auth.OAuthLauncher
import com.ivanlolka.omnistream.model.LiveStream
import com.ivanlolka.omnistream.model.Platform
import kotlinx.coroutines.launch

class StreamsFragment : Fragment(R.layout.fragment_streams) {

    private val viewModel: MainViewModel by activityViewModels()
    private val streamsAdapter = LiveStreamsAdapter(::onStreamClicked)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val twitchStatus = view.findViewById<TextView>(R.id.twitchAuthStatus)
        val vkStatus = view.findViewById<TextView>(R.id.vkAuthStatus)
        val twitchAuthButton = view.findViewById<MaterialButton>(R.id.twitchAuthButton)
        val vkAuthButton = view.findViewById<MaterialButton>(R.id.vkAuthButton)
        val refreshButton = view.findViewById<MaterialButton>(R.id.refreshButton)
        val recycler = view.findViewById<RecyclerView>(R.id.streamsRecycler)
        val emptyText = view.findViewById<TextView>(R.id.emptyStreamsText)

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = streamsAdapter

        twitchAuthButton.setOnClickListener {
            viewModel.createTwitchOAuthRequest()
                .onSuccess { request -> OAuthLauncher.launch(requireContext(), request.authUrl) }
                .onFailure { showShortMessage(it.message ?: "Twitch OAuth failed.") }
        }

        vkAuthButton.isEnabled = false
        vkAuthButton.alpha = 0.6f
        vkAuthButton.setOnClickListener {
            showShortMessage(getString(R.string.vk_auth_temporarily_disabled))
        }

        refreshButton.setOnClickListener { viewModel.refreshSubscriptions() }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.authState.collect { auth ->
                        twitchStatus.text = if (auth.isTwitchAuthorized) {
                            getString(R.string.authorized)
                        } else {
                            getString(R.string.not_authorized)
                        }
                        vkStatus.text = getString(R.string.vk_auth_temporarily_disabled)
                    }
                }

                launch {
                    viewModel.liveStreams.collect { streams ->
                        streamsAdapter.submitList(streams)
                        emptyText.visibility = if (streams.isEmpty()) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.isRefreshing.collect { refreshing ->
                        refreshButton.isEnabled = !refreshing
                        refreshButton.text = if (refreshing) {
                            getString(R.string.scanning)
                        } else {
                            getString(R.string.refresh_subscriptions)
                        }
                    }
                }
            }
        }
    }

    private fun onStreamClicked(stream: LiveStream) {
        when (stream.platform) {
            Platform.TWITCH -> showTwitchOpenDialog(stream)
            Platform.VK -> showVkOpenDialog(stream)
        }
    }

    private fun showTwitchOpenDialog(stream: LiveStream) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.open_stream_options))
            .setItems(
                arrayOf(
                    getString(R.string.watch_native),
                    getString(R.string.select_pair_platform)
                )
            ) { _, which ->
                when (which) {
                    0 -> {
                        viewModel.openNativeStream(stream)
                        navigateToWatch()
                    }
                    1 -> showVkPairSelection(stream)
                }
            }
            .show()
    }

    private fun showVkOpenDialog(stream: LiveStream) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.open_stream_options))
            .setItems(
                arrayOf(
                    getString(R.string.watch_native),
                    getString(R.string.select_pair_platform)
                )
            ) { _, which ->
                when (which) {
                    0 -> {
                        viewModel.openNativeStream(stream)
                        navigateToWatch()
                    }
                    1 -> showTwitchPairSelection(stream)
                }
            }
            .show()
    }

    private fun showVkPairSelection(twitchStream: LiveStream) {
        val vkCandidates = viewModel.liveStreams.value.filter { it.platform == Platform.VK }
        if (vkCandidates.isEmpty()) {
            showManualNicknameDialog(forPlatform = Platform.VK) { nickname ->
                val vkStream = viewModel.createManualVkStream(nickname) ?: return@showManualNicknameDialog
                viewModel.pairStreams(twitchStream, vkStream, Platform.TWITCH)
                navigateToWatch()
            }
            return
        }

        val items = vkCandidates.map { it.displayName }.toMutableList().apply {
            add(getString(R.string.enter_manually))
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.select_pair_platform))
            .setItems(items.toTypedArray()) { _, which ->
                if (which == vkCandidates.size) {
                    showManualNicknameDialog(forPlatform = Platform.VK) { nickname ->
                        val vkStream = viewModel.createManualVkStream(nickname) ?: return@showManualNicknameDialog
                        viewModel.pairStreams(twitchStream, vkStream, Platform.TWITCH)
                        navigateToWatch()
                    }
                } else {
                    viewModel.pairStreams(twitchStream, vkCandidates[which], Platform.TWITCH)
                    navigateToWatch()
                }
            }
            .show()
    }

    private fun showTwitchPairSelection(vkStream: LiveStream) {
        val twitchCandidates = viewModel.liveStreams.value.filter { it.platform == Platform.TWITCH }
        if (twitchCandidates.isEmpty()) {
            showManualNicknameDialog(forPlatform = Platform.TWITCH) { nickname ->
                val twitchStream = viewModel.createManualTwitchStream(nickname) ?: return@showManualNicknameDialog
                viewModel.pairStreams(twitchStream, vkStream, Platform.VK)
                navigateToWatch()
            }
            return
        }

        val items = twitchCandidates.map { it.displayName }.toMutableList().apply {
            add(getString(R.string.enter_manually))
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.select_pair_platform))
            .setItems(items.toTypedArray()) { _, which ->
                if (which == twitchCandidates.size) {
                    showManualNicknameDialog(forPlatform = Platform.TWITCH) { nickname ->
                        val twitchStream = viewModel.createManualTwitchStream(nickname) ?: return@showManualNicknameDialog
                        viewModel.pairStreams(twitchStream, vkStream, Platform.VK)
                        navigateToWatch()
                    }
                } else {
                    viewModel.pairStreams(twitchCandidates[which], vkStream, Platform.VK)
                    navigateToWatch()
                }
            }
            .show()
    }

    private fun showManualNicknameDialog(forPlatform: Platform, onSubmit: (String) -> Unit) {
        val input = TextInputEditText(requireContext()).apply {
            hint = getString(R.string.input_channel_title)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(if (forPlatform == Platform.TWITCH) "Twitch channel" else "VK channel")
            .setView(input)
            .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                onSubmit(input.text?.toString().orEmpty())
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showShortMessage(text: String) {
        Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
    }

    private fun navigateToWatch() {
        (activity as? MainActivity)?.openWatchTab()
    }
}
