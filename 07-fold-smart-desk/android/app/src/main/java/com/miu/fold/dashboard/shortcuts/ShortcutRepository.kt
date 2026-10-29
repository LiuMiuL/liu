package com.miu.fold.dashboard.shortcuts

import com.miu.fold.dashboard.core.AppClock
import com.miu.fold.dashboard.core.Ids
import com.miu.fold.dashboard.data.AppDatabase
import com.miu.fold.dashboard.data.ShortcutEntity
import kotlinx.coroutines.flow.Flow

sealed interface ShortcutSaveResult {
    data class Saved(val shortcut: ShortcutEntity, val warning: String?) : ShortcutSaveResult
    data class Invalid(val reason: String) : ShortcutSaveResult
}

class ShortcutRepository(db: AppDatabase, private val clock: AppClock) {
    private val dao = db.shortcutDao()
    val all: Flow<List<ShortcutEntity>> = dao.observeAll()

    suspend fun get(id: String) = dao.get(id)

    suspend fun save(id: String?, type: ShortcutType, label: String, target: String, fallbackUrl: String?): ShortcutSaveResult {
        var warning: String? = null
        val cleanTarget: String = when (type) {
            ShortcutType.WEB -> when (val r = UrlValidator.check(target)) {
                is UrlCheck.Rejected -> return ShortcutSaveResult.Invalid(r.reason)
                is UrlCheck.Ok -> { if (r.insecure) warning = "암호화되지 않은 http 주소입니다."; r.url }
            }
            ShortcutType.APP -> target.trim().also {
                if (!PackageNameRule.isValid(it)) return ShortcutSaveResult.Invalid("앱을 다시 선택하세요.")
            }
            ShortcutType.INTERNAL -> target.also {
                if (InternalTarget.fromKey(it) == null) return ShortcutSaveResult.Invalid("지원하지 않는 앱 내부 목적지입니다.")
            }
        }
        val cleanFallback = fallbackUrl?.takeIf { it.isNotBlank() }?.let {
            when (val r = UrlValidator.check(it)) {
                is UrlCheck.Rejected -> return ShortcutSaveResult.Invalid("대체 웹 주소: ${r.reason}")
                is UrlCheck.Ok -> r.url
            }
        }
        val name = label.trim().ifEmpty {
            when (type) {
                ShortcutType.WEB -> java.net.URI(cleanTarget).host ?: "웹"
                ShortcutType.APP -> cleanTarget.substringAfterLast('.')
                ShortcutType.INTERNAL -> InternalTarget.fromKey(cleanTarget)!!.label
            }
        }.take(60)
        val old = id?.let { dao.get(it) }
        val entity = ShortcutEntity(
            id = old?.id ?: Ids.newId(), type = type.name, label = name, target = cleanTarget,
            iconRef = null, fallbackUrl = cleanFallback,
            orderKey = old?.orderKey ?: (dao.maxOrder() + 1), updatedAt = clock.nowMillis(),
        )
        dao.upsert(entity)
        return ShortcutSaveResult.Saved(entity, warning)
    }

    suspend fun delete(id: String) = dao.delete(id)
}
