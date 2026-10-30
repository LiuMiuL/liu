package com.miu.videograb

import java.io.File

/** video-downloader/tests/fixtures 의 실제 yt-dlp 출력. */
object Fixtures {
    fun file(name: String): File =
        listOf("../../tests/fixtures/$name", "../tests/fixtures/$name").map(::File).first { it.exists() }

    fun lines(name: String): List<String> = file(name).readLines().filter { it.isNotBlank() && !it.startsWith("#") }
}
