package com.ivanlolka.omnistream.ui

import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.style.ImageSpan
import com.google.android.material.textfield.TextInputEditText
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.max

class InputEmoteStyler(
    private val input: TextInputEditText
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val tokenRegex = Regex("\\S+")
    private val imageCache = LinkedHashMap<String, Drawable>()
    private val pendingUrls = mutableSetOf<String>()
    private var emoteUrls: Map<String, String> = emptyMap()

    fun updateEmoteMap(map: Map<String, String>) {
        emoteUrls = map
        apply()
    }

    fun apply() {
        val editable = input.text ?: return
        clearImageSpans(editable)
        if (editable.isEmpty() || emoteUrls.isEmpty()) return

        val text = editable.toString()
        tokenRegex.findAll(text).forEach { match ->
            val coreRange = findCoreRange(match.value) ?: return@forEach
            val code = match.value.substring(coreRange.first, coreRange.last + 1)
            val url = emoteUrls[code] ?: return@forEach
            val start = match.range.first + coreRange.first
            val end = match.range.first + coreRange.last + 1
            val drawable = imageCache[url]
            if (drawable != null) {
                applyImageSpan(editable, drawable, start, end)
            } else {
                requestDrawable(url)
            }
        }
    }

    fun clear() {
        scope.coroutineContext.cancel()
        pendingUrls.clear()
        imageCache.clear()
    }

    private fun requestDrawable(url: String) {
        if (!pendingUrls.add(url)) return
        scope.launch {
            runCatching {
                val request = ImageRequest.Builder(input.context)
                    .data(url)
                    .allowHardware(false)
                    .build()
                val result = input.context.imageLoader.execute(request)
                val source = result.drawable ?: return@runCatching
                val height = max(input.lineHeight, (input.textSize * 1.35f).toInt())
                val width = max(1, source.intrinsicWidth * height / max(1, source.intrinsicHeight))
                val drawable = source.constantState?.newDrawable()?.mutate() ?: source.mutate()
                drawable.setBounds(0, 0, width, height)
                imageCache[url] = drawable
            }.also {
                pendingUrls.remove(url)
                apply()
            }
        }
    }

    private fun applyImageSpan(editable: Editable, drawable: Drawable, start: Int, end: Int) {
        val span = ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM)
        editable.setSpan(span, start, end, Editable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun clearImageSpans(editable: Editable) {
        editable.getSpans(0, editable.length, ImageSpan::class.java).forEach {
            editable.removeSpan(it)
        }
    }

    private fun findCoreRange(token: String): IntRange? {
        val first = token.indexOfFirst { isCoreChar(it) }
        if (first < 0) return null
        val last = token.indexOfLast { isCoreChar(it) }
        if (last < first) return null
        return first..last
    }

    private fun isCoreChar(char: Char): Boolean {
        return char.isLetterOrDigit() || char == '_' || char == ':'
    }
}
