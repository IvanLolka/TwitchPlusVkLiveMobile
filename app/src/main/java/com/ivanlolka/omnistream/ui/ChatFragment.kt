package com.ivanlolka.omnistream.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.ivanlolka.omnistream.MainViewModel
import com.ivanlolka.omnistream.R
import com.ivanlolka.omnistream.model.ChatEmote
import kotlinx.coroutines.launch

class ChatFragment : Fragment(R.layout.fragment_chat) {

    private val viewModel: MainViewModel by activityViewModels()
    private val chatAdapter = UnifiedChatAdapter()
    private var emoteUrlByCode: Map<String, String> = emptyMap()
    private var allEmotes: List<ChatEmote> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recycler = view.findViewById<RecyclerView>(R.id.chatRecycler)
        val messageInput = view.findViewById<TextInputEditText>(R.id.messageInput)
        val sendButton = view.findViewById<MaterialButton>(R.id.sendButton)
        val emotesButton = view.findViewById<MaterialButton>(R.id.emotesButton)
        val messagePreviewText = view.findViewById<TextView>(R.id.messagePreviewText)

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = chatAdapter

        fun sendMessage() {
            val text = messageInput.text?.toString().orEmpty()
            viewModel.sendUnifiedMessage(text)
            messageInput.setText("")
            updateMessagePreview(messagePreviewText, "")
        }

        sendButton.setOnClickListener { sendMessage() }
        emotesButton.setOnClickListener {
            if (allEmotes.isEmpty()) {
                Toast.makeText(requireContext(), "Эмоты еще загружаются", Toast.LENGTH_SHORT).show()
            } else {
                showEmotePicker(messageInput)
            }
        }

        messageInput.doAfterTextChanged { editable ->
            updateMessagePreview(messagePreviewText, editable?.toString().orEmpty())
        }

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
                    viewModel.chatEmoteUrlMap.collect { emotes ->
                        emoteUrlByCode = emotes
                        chatAdapter.submitEmoteMap(emotes)
                        updateMessagePreview(messagePreviewText, messageInput.text?.toString().orEmpty())
                    }
                }

                launch {
                    viewModel.availableChatEmotes.collect { emotes ->
                        allEmotes = emotes
                    }
                }
            }
        }
    }

    private fun showEmotePicker(messageInput: TextInputEditText) {
        val dialog = BottomSheetDialog(requireContext())
        val root = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_emote_picker, null, false)
        val searchInput = root.findViewById<TextInputEditText>(R.id.emoteSearchInput)
        val recycler = root.findViewById<RecyclerView>(R.id.emoteRecycler)
        val adapter = EmotePickerAdapter { emote ->
            insertEmote(messageInput, emote.code)
            dialog.dismiss()
        }

        recycler.layoutManager = GridLayoutManager(requireContext(), 5)
        recycler.adapter = adapter
        adapter.submitList(allEmotes)

        searchInput.doAfterTextChanged { editable ->
            val query = editable?.toString().orEmpty().trim().lowercase()
            if (query.isBlank()) {
                adapter.submitList(allEmotes)
            } else {
                adapter.submitList(allEmotes.filter { it.code.lowercase().contains(query) })
            }
        }

        dialog.setContentView(root)
        dialog.show()
    }

    private fun insertEmote(messageInput: TextInputEditText, code: String) {
        val editable = messageInput.text ?: return
        val cursor = messageInput.selectionStart.coerceAtLeast(0)
        val prefixSpace = if (cursor > 0 && !editable[cursor - 1].isWhitespace()) " " else ""
        val suffixSpace = if (cursor < editable.length && !editable[cursor].isWhitespace()) " " else " "
        val token = "$prefixSpace$code$suffixSpace"
        editable.insert(cursor, token)
        messageInput.setSelection((cursor + token.length).coerceAtMost(editable.length))
    }

    private fun updateMessagePreview(preview: TextView, text: String) {
        val message = text.trim()
        preview.isVisible = message.isNotBlank()
        if (message.isBlank()) {
            preview.text = ""
            return
        }
        HtmlEmoteRenderer.render(preview, message, emoteUrlByCode)
    }
}
