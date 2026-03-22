package com.ivanlolka.omnistream.ui

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LevelListDrawable
import android.text.Html
import android.text.TextUtils
import android.widget.TextView
import androidx.core.text.HtmlCompat
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.max

object HtmlEmoteRenderer {

    private val imageScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val tokenRegex = Regex("\\S+")

    fun render(textView: TextView, rawText: String, emoteUrlByCode: Map<String, String>) {
        if (rawText.isBlank()) {
            textView.text = ""
            return
        }
        if (emoteUrlByCode.isEmpty()) {
            textView.text = rawText
            return
        }
        val html = buildHtml(rawText, emoteUrlByCode)
        val spanned = HtmlCompat.fromHtml(
            html,
            HtmlCompat.FROM_HTML_MODE_COMPACT,
            CoilImageGetter(textView),
            null
        )
        textView.text = spanned
    }

    private fun buildHtml(text: String, emoteUrlByCode: Map<String, String>): String {
        val builder = StringBuilder(text.length + 64)
        var cursor = 0
        tokenRegex.findAll(text).forEach { match ->
            if (match.range.first > cursor) {
                builder.append(TextUtils.htmlEncode(text.substring(cursor, match.range.first)))
            }
            builder.append(tokenToHtml(match.value, emoteUrlByCode))
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            builder.append(TextUtils.htmlEncode(text.substring(cursor)))
        }
        return builder.toString()
    }

    private fun tokenToHtml(token: String, emoteUrlByCode: Map<String, String>): String {
        val coreRange = findCoreRange(token) ?: return TextUtils.htmlEncode(token)
        val core = token.substring(coreRange.first, coreRange.last + 1)
        val url = emoteUrlByCode[core] ?: return TextUtils.htmlEncode(token)

        val prefix = token.substring(0, coreRange.first)
        val suffix = token.substring(coreRange.last + 1)
        return buildString {
            append(TextUtils.htmlEncode(prefix))
            append("<img src=\"")
            append(TextUtils.htmlEncode(url))
            append("\"/>")
            append(TextUtils.htmlEncode(suffix))
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

    private class CoilImageGetter(
        private val targetView: TextView
    ) : Html.ImageGetter {
        override fun getDrawable(source: String): Drawable {
            val placeholder = ColorDrawable(0x332A3344)
            val size = max(targetView.lineHeight, (targetView.textSize * 1.35f).toInt())
            placeholder.setBounds(0, 0, size, size)

            val wrapper = LevelListDrawable().apply {
                addLevel(0, 0, placeholder)
                setBounds(0, 0, size, size)
                level = 0
            }

            imageScope.launch {
                runCatching {
                    val request = ImageRequest.Builder(targetView.context)
                        .data(source)
                        .allowHardware(false)
                        .build()
                    val result = targetView.context.imageLoader.execute(request)
                    val drawable = result.drawable ?: return@runCatching
                    val height = max(targetView.lineHeight, (targetView.textSize * 1.35f).toInt())
                    val width = max(1, drawable.intrinsicWidth * height / max(1, drawable.intrinsicHeight))
                    drawable.setBounds(0, 0, width, height)
                    wrapper.addLevel(1, 1, drawable)
                    wrapper.setBounds(0, 0, width, height)
                    wrapper.level = 1
                    targetView.text = targetView.text
                    targetView.invalidate()
                }
            }

            return wrapper
        }
    }
}
