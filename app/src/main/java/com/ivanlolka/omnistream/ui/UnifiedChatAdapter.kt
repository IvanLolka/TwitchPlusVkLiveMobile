package com.ivanlolka.omnistream.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ivanlolka.omnistream.R
import com.ivanlolka.omnistream.model.Platform
import com.ivanlolka.omnistream.model.UnifiedChatMessage

class UnifiedChatAdapter : RecyclerView.Adapter<UnifiedChatAdapter.MessageViewHolder>() {

    private val items = mutableListOf<UnifiedChatMessage>()

    fun submitMessages(messages: List<UnifiedChatMessage>) {
        items.clear()
        items.addAll(messages)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(items[position])
    }

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val authorText: TextView = itemView.findViewById(R.id.authorText)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val targetsText: TextView = itemView.findViewById(R.id.targetsText)

        fun bind(item: UnifiedChatMessage) {
            authorText.text = item.author
            messageText.text = item.text
            targetsText.text = if (item.isSystem) {
                "Системное сообщение"
            } else {
                val route = item.targets.joinToString(" + ") { platform ->
                    when (platform) {
                        Platform.TWITCH -> "Twitch"
                        Platform.VK -> "VK"
                    }
                }
                if (route.isBlank()) "Локально" else "Отправлено: $route"
            }
        }
    }
}
