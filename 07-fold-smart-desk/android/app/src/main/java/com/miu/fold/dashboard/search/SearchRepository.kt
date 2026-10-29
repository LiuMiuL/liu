package com.miu.fold.dashboard.search

import com.miu.fold.dashboard.data.AppDatabase
import com.miu.fold.dashboard.data.likePattern
import com.miu.fold.dashboard.notes.NoteRules
import com.miu.fold.dashboard.recent.RecentType

data class SearchHit(val type: RecentType, val id: String, val title: String, val subtitle: String)

/** 검색 범위: 이 앱의 메모·할 일·일정·등록 바로가기. 다른 앱·PC·웹은 검색하지 않는다. */
class SearchRepository(private val db: AppDatabase) {
    suspend fun search(query: String, types: Set<RecentType>): List<SearchHit> {
        if (query.isBlank()) return emptyList()
        val q = likePattern(query)
        val out = mutableListOf<SearchHit>()
        if (RecentType.NOTE in types) db.noteDao().search(q).mapTo(out) {
            SearchHit(RecentType.NOTE, it.id, NoteRules.suggestedTitle(it.title, it.body), snippet(it.body, query))
        }
        if (RecentType.TODO in types) db.todoDao().search(q).mapTo(out) {
            SearchHit(RecentType.TODO, it.id, it.title, if (it.doneAt != null) "완료" else (it.dueDate ?: "날짜 없음"))
        }
        if (RecentType.EVENT in types) db.eventDao().search(q).mapTo(out) {
            SearchHit(RecentType.EVENT, it.id, it.title, it.localDateStart)
        }
        if (RecentType.SHORTCUT in types) db.shortcutDao().search(q).mapTo(out) {
            SearchHit(RecentType.SHORTCUT, it.id, it.label, it.target)
        }
        return out
    }

    private fun snippet(body: String, q: String): String {
        val i = body.indexOf(q.trim(), ignoreCase = true)
        if (i < 0) return body.take(60).replace('\n', ' ')
        val start = (i - 20).coerceAtLeast(0)
        return (if (start > 0) "…" else "") + body.substring(start, (i + 60).coerceAtMost(body.length)).replace('\n', ' ')
    }
}
