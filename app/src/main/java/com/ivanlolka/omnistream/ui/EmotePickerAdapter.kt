package com.ivanlolka.omnistream.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.ivanlolka.omnistream.R
import com.ivanlolka.omnistream.model.ChatEmote

class EmotePickerAdapter(
    private val onEmoteClick: (ChatEmote) -> Unit
) : RecyclerView.Adapter<EmotePickerAdapter.EmoteViewHolder>() {

    private val items = mutableListOf<ChatEmote>()

    fun submitList(data: List<ChatEmote>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmoteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_emote_picker, parent, false)
        return EmoteViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: EmoteViewHolder, position: Int) {
        holder.bind(items[position], onEmoteClick)
    }

    class EmoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val emoteImage: ImageView = itemView.findViewById(R.id.emoteImage)
        private val emoteCode: TextView = itemView.findViewById(R.id.emoteCodeText)

        fun bind(item: ChatEmote, onClick: (ChatEmote) -> Unit) {
            emoteImage.load(item.imageUrl)
            emoteCode.text = item.code
            itemView.setOnClickListener { onClick(item) }
        }
    }
}
