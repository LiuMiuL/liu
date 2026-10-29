package com.miu.fold.dashboard.shortcuts

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.core.net.toUri
import com.miu.fold.dashboard.data.ShortcutEntity

sealed interface LaunchResult {
    data object Opened : LaunchResult
    data class Internal(val target: InternalTarget) : LaunchResult
    data class NotFound(val shortcut: ShortcutEntity, val reason: String) : LaunchResult
}

data class LaunchableApp(val packageName: String, val label: String)

/**
 * 외부 목적지 해석. 전체 설치 앱 목록 권한(QUERY_ALL_PACKAGES)은 쓰지 않고,
 * 매니페스트 <queries>에 선언한 런처 인텐트로 보이는 앱만 다룬다.
 */
object ShortcutLauncher {
    fun launch(context: Context, s: ShortcutEntity): LaunchResult {
        val type = runCatching { ShortcutType.valueOf(s.type) }.getOrNull()
            ?: return LaunchResult.NotFound(s, "알 수 없는 바로가기 종류입니다.")
        return when (type) {
            ShortcutType.INTERNAL -> InternalTarget.fromKey(s.target)?.let { LaunchResult.Internal(it) }
                ?: LaunchResult.NotFound(s, "지원하지 않는 앱 내부 목적지입니다.")
            ShortcutType.WEB -> openWeb(context, s.target, s)
            ShortcutType.APP -> {
                val intent = context.packageManager.getLaunchIntentForPackage(s.target)
                    ?: return LaunchResult.NotFound(s, "대상을 찾을 수 없습니다. 앱이 삭제되었거나 실행할 수 없습니다.")
                try {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    LaunchResult.Opened
                } catch (e: ActivityNotFoundException) {
                    LaunchResult.NotFound(s, "대상을 찾을 수 없습니다.")
                } catch (e: SecurityException) {
                    LaunchResult.NotFound(s, "이 앱은 바로가기로 열 수 없습니다.")
                }
            }
        }
    }

    fun openWeb(context: Context, url: String, s: ShortcutEntity?): LaunchResult {
        val ok = UrlValidator.check(url) as? UrlCheck.Ok
            ?: return LaunchResult.NotFound(s ?: dummy(url), "안전하지 않은 주소라 열지 않았습니다.")
        return try {
            val intent = Intent(Intent.ACTION_VIEW, ok.url.toUri())
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            LaunchResult.Opened
        } catch (e: ActivityNotFoundException) {
            LaunchResult.NotFound(s ?: dummy(url), "이 주소를 열 수 있는 앱(브라우저)이 없습니다.")
        }
    }

    fun launchableApps(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = if (android.os.Build.VERSION.SDK_INT >= 33) pm.queryIntentActivities(main, PackageManager.ResolveInfoFlags.of(0))
        else @Suppress("DEPRECATION") pm.queryIntentActivities(main, 0)
        return infos
            .map { LaunchableApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun appIcon(context: Context, pkg: String): Drawable? =
        runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull()

    private fun dummy(url: String) = ShortcutEntity("", ShortcutType.WEB.name, url, url, updatedAt = 0)
}

