package com.miu.videograb.queue

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager

/**
 * 대기열 처리 작업을 예약한다. 같은 이름의 작업에 이어 붙여(APPEND_OR_REPLACE) 한 번에 하나만 돈다.
 * 처리 중에 새 링크가 들어와도 뒤에 붙은 작업이 남은 대기열을 다시 확인한다.
 */
class DownloadScheduler(private val context: Context) {
    fun enqueue(wifiOnly: Boolean) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .build(),
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    companion object {
        const val UNIQUE_NAME = "download-queue"
        const val TAG = "download"
    }
}
