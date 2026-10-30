package com.miu.videograb.save

import com.miu.videograb.engine.OutputFiles

data class SavedItem(val uri: String, val bytes: Long)

/** 받은 파일을 사용자가 볼 수 있는 곳(갤러리·음악)에 저장한다. */
interface MediaSaver {
    suspend fun save(media: OutputFiles.Media, displayName: String): SavedItem
    suspend fun delete(uri: String)
}
