package com.ivanlolka.omnistream.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.ivanlolka.omnistream.MainViewModel
import com.ivanlolka.omnistream.R
import kotlinx.coroutines.launch

class ChatFragment : Fragment(R.layout.fragment_chat) {

    private val viewModel: MainViewModel by activityViewModels()
    private val chatAdapter = UnifiedChatAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recycler = view.findViewById<RecyclerView>(R.id.chatRecycler)
        val messageInput = view.findViewById<TextInputEditText>(R.id.messageInput)
        val sendButton = view.findViewById<MaterialButton>(R.id.sendButton)
        val connectionStateText = view.findViewById<TextView>(R.id.connectionStateText)
        val chatTargetsText = view.findViewById<TextView>(R.id.chatTargetsText)

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = chatAdapter

        fun sendMessage() {
            val text = messageInput.text?.toString().orEmpty()
            viewModel.sendUnifiedMessage(text)
            messageInput.setText("")
        }

        sendButton.setOnClickListener { sendMessage() }
        messageInput.setOnEditorActionListener { _, actionId, event ->
            val isSendAction = actionId == EditorInfo.IME_ACTION_SEND
            val isEnterDown = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (isSendAction || isEnterDown) {
                sendMessage()
                true
            } else {
                false
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.chatMessages.collect { messages ->
                        chatAdapter.submitMessages(messages)
                        if (messages.isNotEmpty()) {
                            recycler.scrollToPosition(messages.lastIndex)
                        }
                    }
                }

                launch {
                    viewModel.twitchConnectionLabel.collect { label ->
                        connectionStateText.text = getString(R.string.chat_connection, label)
                    }
                }

                launch {
                    viewModel.chatTargetLabel.collect { label ->
                        chatTargetsText.text = getString(R.string.chat_targets, label)
                    }
                }
            }
        }
    }
}
