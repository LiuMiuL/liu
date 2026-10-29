package com.miu.fold.deals

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.miu.fold.deals.data.BoardConditions
import com.miu.fold.deals.data.BoardConditionsStore
import com.miu.fold.deals.listings.Category
import com.miu.fold.deals.listings.SpecFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** 저장한 DataStore를 닫고 새 인스턴스로 다시 읽어 "앱을 껐다 켜도 유지"를 파일 수준에서 확인한다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BoardConditionsStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val file = File(context.filesDir, "datastore/test_conditions.preferences_pb").also { it.delete() }

    private fun <T> withStore(block: suspend (BoardConditionsStore) -> T): T {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            // 이전 인스턴스의 파일 점유 해제가 비동기라 잠시 재시도한다(테스트에서만 같은 파일을 여러 번 연다).
            repeat(50) { attempt ->
                try {
                    return runBlocking { block(BoardConditionsStore(PreferenceDataStoreFactory.create(scope = scope) { file })) }
                } catch (e: IllegalStateException) {
                    if (attempt == 49 || e.message?.contains("multiple DataStores") != true) throw e
                    Thread.sleep(20)
                }
            }
            error("unreachable")
        } finally {
            // 스코프를 닫아야 같은 파일로 새 DataStore를 만들 수 있다(앱 종료와 같은 효과).
            scope.cancel()
            runBlocking { scope.coroutineContext[kotlinx.coroutines.Job]!!.join() }
        }
    }

    // 저장한 적 없으면 null(기본 조건 사용)
    @Test fun nothingSavedReturnsNull() {
        assertNull(withStore { it.load() })
    }

    // 모든 조건이 새 인스턴스에서 그대로 복원된다
    @Test fun conditionsSurviveReopen() {
        val saved = BoardConditions(
            sort = "TOTAL_ASC", onSaleOnly = true, hideExcluded = false,
            spec = SpecFilter(
                category = Category.PHONE, models = setOf("z fold6", "z fold5"), ramGb = setOf(12, 16),
                storageGb = setOf(512, 1024), publishedWithinDays = 7, checkedWithinDays = 30, includeUnknown = true,
            ),
        )
        withStore { it.save(saved) }
        assertEquals(saved, withStore { it.load() })
    }

    // 조건을 해제하면 저장값에서도 빠진다(이전 값이 남지 않음)
    @Test fun clearedConditionsAreRemoved() {
        withStore { it.save(BoardConditions("SAVED_DESC", false, true, SpecFilter(category = Category.PHONE, publishedWithinDays = 7, ramGb = setOf(12)))) }
        withStore { it.save(BoardConditions("SAVED_DESC", false, true, SpecFilter())) }
        assertEquals(SpecFilter(), withStore { it.load() }!!.spec)
    }
}
