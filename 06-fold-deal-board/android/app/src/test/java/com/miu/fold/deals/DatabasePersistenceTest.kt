package com.miu.fold.deals

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import com.miu.fold.deals.comparison.Comparison
import com.miu.fold.deals.data.DealDatabase
import com.miu.fold.deals.data.ListingRepository
import com.miu.fold.deals.listings.ListingDraft
import com.miu.fold.deals.observations.ShippingState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 파일 DB를 닫았다가 다시 열어 "재시작 후 보존"을 확인한다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabasePersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "persist-test.db"
    private var clock = Fixtures.NOW

    @Before fun clean() { context.deleteDatabase(name) }
    @After fun cleanup() { context.deleteDatabase(name) }

    private fun open() = DealDatabase.open(context, name).let { it to ListingRepository(it.dao()) { clock } }

    // 5개 저장·수정·삭제 후 재실행해도 데이터와 가격 이력이 보존된다
    @Test fun fiveListingsSurviveReopenWithHistory() = runTest {
        val (db1, repo1) = open()
        val prices = listOf("70만", "68만", "65만", "가격 협의", "")
        val ids = prices.mapIndexed { i, p ->
            val d = ListingDraft(modelRaw = "Galaxy Z Fold6", ram = "12GB", storage = "512GB", price = p,
                shippingState = if (i == 0) ShippingState.INCLUDED else ShippingState.UNKNOWN)
            val id = "L$i"
            repo1.create(d.toListing(id, null, clock), d.toObservationIfChanged(id, "L$i-o1", null, clock))
            id
        }
        // 가격 변경 → 새 관측 추가(덮어쓰지 않음)
        clock += 60_000
        val existing = repo1.get("L0")!!
        val edit = ListingDraft.fromListing(existing, repo1.observations("L0").last()).copy(price = "65만", memo = "직거래 가능 문의")
        repo1.update(edit.toListing("L0", existing, clock), edit.toObservationIfChanged("L0", "L0-o2", repo1.observations("L0").last(), clock))
        repo1.softDelete("L4")
        repo1.saveComparison(Comparison("c1", "A vs B", "L0\nL1", "L0-o1\nL1-o1", 650_000, null, clock))
        db1.close()

        val (db2, repo2) = open()
        val all = repo2.allItems.first()
        assertEquals(5, all.size)
        assertEquals(4, all.count { it.listing.deletedAt == null })
        val l0 = all.first { it.listing.id == "L0" }
        assertEquals(listOf(700_000L, 650_000L), l0.observations.map { it.amountKrw })
        assertEquals("L0-o2", l0.latestPosted!!.id)
        assertEquals("직거래 가능 문의", l0.listing.memo)
        assertNull(all.first { it.listing.id == "L4" }.latestPosted)
        assertNotNull(all.first { it.listing.id == "L4" }.listing.deletedAt)
        // 비교 스냅샷은 당시 관측 ID를 유지한다(MKT-T18).
        val c = repo2.comparisons.first().single()
        assertEquals(listOf("L0-o1", "L1-o1"), c.observationIdList)
        assertEquals(ids.take(2), c.listingIdList)
        db2.close()
    }

    // 같은 관측 ID 재삽입은 거절되어 이력이 덮어써지지 않는다
    @Test fun duplicateObservationIdIsRejected() = runTest {
        val (db, repo) = open()
        val d = ListingDraft(modelRaw = "Fold6", price = "70만")
        repo.create(d.toListing("L", null, clock), d.toObservationIfChanged("L", "o1", null, clock))
        try {
            repo.addObservation(d.copy(price = "1원").toObservationIfChanged("L", "o1", null, clock)!!)
            fail("중복 ID가 허용됨")
        } catch (_: SQLiteConstraintException) {
        }
        assertEquals(700_000L, repo.observations("L").single().amountKrw)
        db.close()
    }
}
