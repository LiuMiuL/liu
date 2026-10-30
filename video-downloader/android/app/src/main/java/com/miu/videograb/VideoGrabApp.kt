package com.miu.videograb

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.miu.videograb.data.JobDatabase
import com.miu.videograb.data.JobRepository
import com.miu.videograb.data.SettingsStore
import com.miu.videograb.engine.YtDlpEngine
import com.miu.videograb.queue.AutoEngineUpdater
import com.miu.videograb.queue.DownloadScheduler
import com.miu.videograb.queue.Notifications
import com.miu.videograb.save.MediaStoreSaver
import java.io.File

class VideoGrabApp : Application() {
    val repository by lazy { JobRepository(JobDatabase.open(this).jobs()) }
    val settings by lazy {
        SettingsStore(PreferenceDataStoreFactory.create { File(filesDir, "datastore/${SettingsStore.FILE_NAME}") })
    }
    val engine by lazy { YtDlpEngine(this) }
    val saver by lazy { MediaStoreSaver(this) }
    val scheduler by lazy { DownloadScheduler(this) }
    val engineUpdater by lazy { AutoEngineUpdater(engine, settings) }

    /** 받는 중인 임시 파일 위치. 앱 전용 저장소라 다른 앱에서 보이지 않는다. */
    val workRoot: File get() = File(noBackupFilesDir, "downloads")

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}
