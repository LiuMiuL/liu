package com.miu.videograb.save

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.miu.videograb.engine.OutputFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * MediaStore에 저장한다. 영상은 Movies/VideoGrab, 소리는 Music/VideoGrab.
 * 앱이 직접 만든 항목이라 저장소 권한이 필요 없다(Android 10+).
 */
class MediaStoreSaver(private val context: Context) : MediaSaver {

    override suspend fun save(media: OutputFiles.Media, displayName: String): SavedItem = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val (collection, dir) = when (media.kind) {
            OutputFiles.Kind.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                "${Environment.DIRECTORY_MOVIES}/$FOLDER"
            OutputFiles.Kind.AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                "${Environment.DIRECTORY_MUSIC}/$FOLDER"
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, media.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, dir)
            // 복사가 끝나기 전에는 다른 앱에 보이지 않게 한다.
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("갤러리에 항목을 만들지 못했어요")
        try {
            resolver.openOutputStream(uri)?.use { out -> media.file.inputStream().use { it.copyTo(out) } }
                ?: throw IOException("갤러리 파일을 열지 못했어요")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            SavedItem(uri.toString(), media.file.length())
        } catch (e: Exception) {
            // 반쯤 쓴 항목을 남기지 않는다.
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    override suspend fun delete(uri: String) {
        withContext(Dispatchers.IO) { runCatching { context.contentResolver.delete(Uri.parse(uri), null, null) } }
    }

    companion object {
        const val FOLDER = "VideoGrab"
    }
}
