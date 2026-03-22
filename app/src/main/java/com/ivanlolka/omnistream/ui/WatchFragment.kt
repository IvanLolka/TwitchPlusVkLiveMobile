package com.ivanlolka.omnistream.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.ivanlolka.omnistream.R

class WatchFragment : Fragment(R.layout.fragment_watch) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null) {
            childFragmentManager.commit {
                replace(R.id.playerHost, PlayerFragment(), "watch_player")
                replace(R.id.chatHost, ChatFragment(), "watch_chat")
            }
        }
    }
}
