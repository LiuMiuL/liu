package com.miu.fold.dashboard.shortcuts

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.miu.fold.dashboard.data.ShortcutEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 앱 바로가기 앞에 붙는 앱 아이콘. 앱이 없거나 웹·앱 안 바로가기면 그리지 않는다. */
@Composable
fun ShortcutIcon(s: ShortcutEntity) {
    if (s.type != ShortcutType.APP.name) return
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, s.target) {
        value = withContext(Dispatchers.IO) {
            runCatching { ShortcutLauncher.appIcon(context, s.target)?.toBitmap(72, 72)?.asImageBitmap() }.getOrNull()
        }
    }
    // 이름이 옆에 있으므로 장식용 이미지로 둔다(같은 내용을 두 번 읽지 않게).
    icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(24.dp)) }
}
