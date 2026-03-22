package com.ivanlolka.omnistream.ui

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
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

class WatchFragment : Fragment(R.layout.fragment_watch) {

    private val viewModel: MainViewModel by activityViewModels()
    private var playerCollapsed = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null) {
            childFragmentManager.commit {
                replace(R.id.playerHost, PlayerFragment(), "watch_player")
                replace(R.id.chatHost, ChatFragment(), "watch_chat")
            }
        } else {
            playerCollapsed = savedInstanceState.getBoolean(KEY_PLAYER_COLLAPSED, false)
        }

        val playerHost = view.findViewById<FrameLayout>(R.id.playerHost)
        val collapseBar = view.findViewById<LinearLayout>(R.id.playerCollapseBar)
        val toggleButton = view.findViewById<MaterialButton>(R.id.togglePlayerSizeButton)
        val platformToggle = view.findViewById<MaterialButtonToggleGroup>(R.id.watchPlatformToggle)
        val twitchToggle = view.findViewById<MaterialButton>(R.id.watchTwitchToggleButton)
        val vkToggle = view.findViewById<MaterialButton>(R.id.watchVkToggleButton)

        toggleButton.setOnClickListener {
            playerCollapsed = !playerCollapsed
            applyPlayerState(playerHost, collapseBar, toggleButton)
        }

        platformToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.watchTwitchToggleButton -> viewModel.switchActivePlatform(Platform.TWITCH)
                R.id.watchVkToggleButton -> viewModel.switchActivePlatform(Platform.VK)
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
                            syncToggleState(platformToggle, twitchToggle, vkToggle, active, twitch, vk)
                        }
                }
            }
        }

        applyPlayerState(playerHost, collapseBar, toggleButton)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_PLAYER_COLLAPSED, playerCollapsed)
    }

    private fun applyPlayerState(
        playerHost: FrameLayout,
        collapseBar: LinearLayout,
        toggleButton: MaterialButton
    ) {
        val playerParams = playerHost.layoutParams as ConstraintLayout.LayoutParams
        val barParams = collapseBar.layoutParams as ConstraintLayout.LayoutParams

        if (playerCollapsed) {
            playerParams.height = dp(42)
            playerParams.dimensionRatio = null
            barParams.height = dp(14)
            toggleButton.text = "^"
        } else {
            playerParams.height = 0
            playerParams.dimensionRatio = "16:9"
            barParams.height = dp(22)
            toggleButton.text = "v"
        }

        playerHost.layoutParams = playerParams
        collapseBar.layoutParams = barParams
    }

    private fun syncToggleState(
        toggleGroup: MaterialButtonToggleGroup,
        twitchToggle: MaterialButton,
        vkToggle: MaterialButton,
        active: Platform,
        twitch: LiveStream?,
        vk: LiveStream?
    ) {
        twitchToggle.isEnabled = twitch != null
        vkToggle.isEnabled = vk != null

        val desired = when {
            active == Platform.TWITCH && twitch != null -> R.id.watchTwitchToggleButton
            active == Platform.VK && vk != null -> R.id.watchVkToggleButton
            twitch != null -> R.id.watchTwitchToggleButton
            vk != null -> R.id.watchVkToggleButton
            else -> R.id.watchTwitchToggleButton
        }
        if (toggleGroup.checkedButtonId != desired) {
            toggleGroup.check(desired)
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private companion object {
        const val KEY_PLAYER_COLLAPSED = "key_player_collapsed"
    }
}
