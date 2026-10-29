package com.miu.fold.dashboard.dashboard

import androidx.room.withTransaction
import com.miu.fold.dashboard.core.Ids
import com.miu.fold.dashboard.data.AppDatabase
import com.miu.fold.dashboard.data.DashboardCardEntity
import com.miu.fold.dashboard.data.LayoutPlacementEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class DashboardState(
    val cards: List<DashboardCardEntity>,
    val layouts: Map<LayoutProfile, List<Placement>>,
)

class DashboardRepository(private val db: AppDatabase) {
    private val dao = db.dashboardDao()

    val state: Flow<DashboardState> = combine(dao.observeCards(), dao.observePlacements()) { cards, placements ->
        val layouts = LayoutProfile.entries.associateWith { profile ->
            LayoutEditor.sorted(
                placements.filter { it.profile == profile.key }.map { Placement(it.cardId, it.orderKey, it.span, it.visible) },
            )
        }
        DashboardState(cards.filter { it.enabled }, layouts)
    }

    /**
     * 첫 실행이면 기본 카드와 두 프로필 기본 배치를 만든다.
     * 이후 실행에서는 배치가 없는 카드만 맨 뒤에 추가하고 기존 사용자 배치는 덮어쓰지 않는다.
     */
    suspend fun ensureDefaults() = db.withTransaction {
        val existing = dao.cards()
        if (existing.isEmpty()) {
            val cards = CardType.entries.map { DashboardCardEntity(Ids.newId(), it.typeId, it.defaultTitle) }
            dao.insertCards(cards)
            val infos = cards.map { CardInfo(it.id, it.type) }
            for (profile in LayoutProfile.entries) {
                dao.replaceProfile(profile.key, DefaultLayouts.placements(infos, profile).map { it.toEntity(profile) })
            }
        } else {
            val infos = existing.filter { it.enabled }.map { CardInfo(it.id, it.type) }
            for (profile in LayoutProfile.entries) {
                val current = dao.placements(profile.key).map { Placement(it.cardId, it.orderKey, it.span, it.visible) }
                val reconciled = LayoutEditor.reconcile(current, infos, profile)
                if (reconciled != LayoutEditor.normalize(current)) {
                    dao.replaceProfile(profile.key, reconciled.map { it.toEntity(profile) })
                }
            }
        }
    }

    /** 편집 저장: 해당 프로필만 트랜잭션으로 교체. */
    suspend fun saveLayout(profile: LayoutProfile, items: List<Placement>) {
        dao.replaceProfile(profile.key, LayoutEditor.normalize(items).map { it.toEntity(profile) })
    }

    /** 선택한 프로필 배치만 기본값으로. 카드·메모·할 일 데이터는 건드리지 않는다. */
    suspend fun resetLayout(profile: LayoutProfile) = db.withTransaction {
        val infos = dao.cards().filter { it.enabled }.map { CardInfo(it.id, it.type) }
        dao.replaceProfile(profile.key, DefaultLayouts.placements(infos, profile).map { it.toEntity(profile) })
    }

    /** 카드 추가: 종류별 수량 제한을 지킨다. 두 프로필 모두 맨 뒤에 보이는 상태로 붙는다. */
    suspend fun addCard(type: CardType): Boolean = db.withTransaction {
        val cards = dao.cards()
        if (cards.count { it.type == type.typeId && it.enabled } >= type.maxInstances) return@withTransaction false
        dao.upsertCard(DashboardCardEntity(Ids.newId(), type.typeId, type.defaultTitle))
        ensureDefaults()
        true
    }

    /** 카드 삭제는 카드 구성만 지운다. 메모·할 일 등 실제 데이터는 남는다. */
    suspend fun deleteCard(cardId: String) = dao.deleteCard(cardId)

    private fun Placement.toEntity(profile: LayoutProfile) =
        LayoutPlacementEntity(cardId, profile.key, orderKey, span, visible)
}
