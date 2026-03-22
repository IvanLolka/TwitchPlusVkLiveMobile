package com.ivanlolka.omnistream.ui

import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.ivanlolka.omnistream.R
import com.ivanlolka.omnistream.model.UnifiedChatMessage

class UnifiedChatAdapter : RecyclerView.Adapter<UnifiedChatAdapter.MessageViewHolder>() {

    private val items = mutableListOf<UnifiedChatMessage>()
    private var emoteUrlByCode: Map<String, String> = emptyMap()

    fun submitMessages(messages: List<UnifiedChatMessage>) {
        items.clear()
        items.addAll(messages)
        notifyDataSetChanged()
    }

    fun submitEmoteMap(emotes: Map<String, String>) {
        emoteUrlByCode = emotes
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(items[position], emoteUrlByCode)
    }

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val headerRow: LinearLayout = itemView.findViewById(R.id.headerRow)
        private val badgesContainer: LinearLayout = itemView.findViewById(R.id.badgesContainer)
        private val authorText: TextView = itemView.findViewById(R.id.authorText)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)

        fun bind(item: UnifiedChatMessage, emoteUrlByCode: Map<String, String>) {
            if (item.isSystem) {
                headerRow.visibility = View.GONE
                messageText.setTextColor(ContextCompat.getColor(itemView.context, R.color.chat_muted_text))
                messageText.text = item.text
                return
            }

            headerRow.visibility = View.VISIBLE
            authorText.text = item.author
            authorText.setTextColor(parseAuthorColor(item.authorColorHex))

            badgesContainer.removeAllViews()
            item.badgeImageUrls.forEach { badgeUrl ->
                badgesContainer.addView(createBadgeImage(badgeUrl))
            }

            messageText.setTextColor(ContextCompat.getColor(itemView.context, R.color.chat_text))
            HtmlEmoteRenderer.render(messageText, item.text, emoteUrlByCode)
        }

        private fun createBadgeImage(url: String): View {
            val size = dp(18)
            val imageView = ImageView(itemView.context).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    rightMargin = dp(2)
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                load(url)
            }
            return imageView
        }

        private fun parseAuthorColor(raw: String?): Int {
            if (raw.isNullOrBlank()) {
                return ContextCompat.getColor(itemView.context, R.color.chat_author_default)
            }
            return runCatching { Color.parseColor(raw) }
                .getOrElse { ContextCompat.getColor(itemView.context, R.color.chat_author_default) }
        }

        private fun dp(value: Int): Int {
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value.toFloat(),
                itemView.resources.displayMetrics
            ).toInt()
        }
    }
}
