package com.miu.fold.dashboard

import android.content.Context
import com.miu.fold.dashboard.backup.BackupService
import com.miu.fold.dashboard.calendar.EventRepository
import com.miu.fold.dashboard.core.AndroidClock
import com.miu.fold.dashboard.core.AppClock
import com.miu.fold.dashboard.dashboard.DashboardRepository
import com.miu.fold.dashboard.data.AppDatabase
import com.miu.fold.dashboard.notes.NoteRepository
import com.miu.fold.dashboard.recent.RecentRepository
import com.miu.fold.dashboard.search.SearchRepository
import com.miu.fold.dashboard.settings.SettingsRepository
import com.miu.fold.dashboard.shortcuts.ShortcutRepository
import com.miu.fold.dashboard.timer.TimerController
import com.miu.fold.dashboard.todos.TodoRepository
import java.io.File

/** 작은 앱이라 DI 프레임워크 대신 수동 컨테이너를 쓴다. */
class AppContainer(context: Context, val clock: AppClock = AndroidClock(context.contentResolver)) {
    val db: AppDatabase = AppDatabase.build(context)
    val dashboard = DashboardRepository(db)
    val notes = NoteRepository(db, clock)
    val todos = TodoRepository(db, clock)
    val events = EventRepository(db, clock)
    val shortcuts = ShortcutRepository(db, clock)
    val recent = RecentRepository(db, clock)
    val search = SearchRepository(db)
    val timer = TimerController(db.timerDao(), clock)
    val settings = SettingsRepository(context)
    val backup = BackupService(db, dashboard, clock, File(context.filesDir, "backups"))
}
