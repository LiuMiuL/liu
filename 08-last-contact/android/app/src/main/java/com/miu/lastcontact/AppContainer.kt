package com.miu.lastcontact

import android.content.Context
import com.miu.lastcontact.backup.BackupService
import com.miu.lastcontact.capture.CallSmsImporter
import com.miu.lastcontact.core.AndroidClock
import com.miu.lastcontact.core.AppClock
import com.miu.lastcontact.data.AppDatabase
import com.miu.lastcontact.data.ContactRepository
import com.miu.lastcontact.settings.SettingsRepository
import java.io.File

class AppContainer(context: Context, val clock: AppClock = AndroidClock()) {
    val db = AppDatabase.build(context)
    val repo = ContactRepository(db, clock)
    val settings = SettingsRepository(context)
    val callSms = CallSmsImporter(context.applicationContext, repo, settings)
    val backup = BackupService(db, clock, File(context.filesDir, "backups"))
}
