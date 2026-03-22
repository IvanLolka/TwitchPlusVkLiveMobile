package com.ivanlolka.omnistream.ui

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.google.android.material.button.MaterialButton
import com.ivanlolka.omnistream.R

class WatchFragment : Fragment(R.layout.fragment_watch) {

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
        val chatHost = view.findViewById<FrameLayout>(R.id.chatHost)
        val collapseBar = view.findViewById<LinearLayout>(R.id.playerCollapseBar)
        val toggleButton = view.findViewById<MaterialButton>(R.id.togglePlayerSizeButton)

        toggleButton.setOnClickListener {
            playerCollapsed = !playerCollapsed
            applyPlayerState(playerHost, chatHost, collapseBar, toggleButton)
        }
        applyPlayerState(playerHost, chatHost, collapseBar, toggleButton)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_PLAYER_COLLAPSED, playerCollapsed)
    }

    private fun applyPlayerState(
        playerHost: FrameLayout,
        chatHost: FrameLayout,
        collapseBar: LinearLayout,
        toggleButton: MaterialButton
    ) {
        val playerParams = playerHost.layoutParams as LinearLayout.LayoutParams
        val chatParams = chatHost.layoutParams as LinearLayout.LayoutParams
        val barParams = collapseBar.layoutParams as LinearLayout.LayoutParams

        if (playerCollapsed) {
            playerParams.height = dp(42)
            playerParams.weight = 0f
            chatParams.height = 0
            chatParams.weight = 1f
            barParams.height = dp(14)
            toggleButton.text = "˄"
        } else {
            playerParams.height = 0
            playerParams.weight = 11f
            chatParams.height = 0
            chatParams.weight = 9f
            barParams.height = dp(22)
            toggleButton.text = "˅"
        }

        playerHost.layoutParams = playerParams
        chatHost.layoutParams = chatParams
        collapseBar.layoutParams = barParams
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
