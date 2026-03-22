package com.ivanlolka.omnistream.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.ivanlolka.omnistream.R
import com.ivanlolka.omnistream.model.LiveStream
import com.ivanlolka.omnistream.model.Platform

class LiveStreamsAdapter(
    private val onStreamClick: (LiveStream) -> Unit
) : RecyclerView.Adapter<LiveStreamsAdapter.StreamViewHolder>() {

    private val items = mutableListOf<LiveStream>()

    fun submitList(streams: List<LiveStream>) {
        items.clear()
        items.addAll(streams)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StreamViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_stream, parent, false)
        return StreamViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: StreamViewHolder, position: Int) {
        holder.bind(items[position], onStreamClick)
    }

    class StreamViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val platformText: TextView = itemView.findViewById(R.id.platformText)
        private val channelText: TextView = itemView.findViewById(R.id.channelText)
        private val titleText: TextView = itemView.findViewById(R.id.titleText)

        fun bind(item: LiveStream, onClick: (LiveStream) -> Unit) {
            val isTwitch = item.platform == Platform.TWITCH
            platformText.text = if (isTwitch) "TWITCH" else "VK"
            platformText.background = ContextCompat.getDrawable(
                itemView.context,
                if (isTwitch) R.drawable.bg_tag_twitch else R.drawable.bg_tag_vk
            )
            channelText.text = item.displayName
            titleText.text = item.title
            itemView.setOnClickListener { onClick(item) }
        }
    }
}
