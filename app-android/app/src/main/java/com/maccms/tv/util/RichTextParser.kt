package com.maccms.tv.util

import android.text.Html
import android.text.Spanned

/** 富文本解析工具：把 maccms 的 HTML（<p>/<br> 等）转换成可读文本 */
object RichTextParser {

    /**
     * 解析 HTML 富文本为纯文本。
     * maccms vod_content 可能返回 "<p>xxx</p>" 或含 <br/> 的 HTML。
     * 用 Android 内置 Html.fromHtml 解析，保留段落/换行语义，去掉标签。
     */
    fun toPlainText(html: String?): String {
        if (html.isNullOrBlank()) return ""
        // 预先把常见的 <p>/<br> 转换成换行，fromHtml 也能处理但这里兜底
        val normalized = html
            .replace("</p>", "\n\n")
            .replace("<br/>", "\n")
            .replace("<br />", "\n")
            .replace("<br>", "\n")
        return Html.fromHtml(normalized, Html.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
}
