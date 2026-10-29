package com.miu.fold.dashboard.notes

object NoteRules {
    const val TITLE_MAX = 40
    const val BODY_MAX = 200_000
    const val SHARED_TEXT_MAX = 20_000
    const val AUTOSAVE_DEBOUNCE_MS = 500L

    /** 제목이 비어 있으면 본문의 첫 줄(최대 40자)을 제안 제목으로 쓴다. */
    fun suggestedTitle(title: String, body: String): String {
        if (title.isNotBlank()) return title.trim()
        val first = body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return "제목 없음"
        return if (first.length > TITLE_MAX) first.take(TITLE_MAX) + "…" else first
    }

    fun parseTags(raw: String): List<String> =
        raw.split(',', '#', ' ').map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(20)

    fun toMarkdown(title: String, body: String): String {
        val t = suggestedTitle(title, body)
        return if (title.isBlank()) body else "# $t\n\n$body"
    }
}
