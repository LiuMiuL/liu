package com.miu.videograb

import com.miu.videograb.engine.OutputFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OutputFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun firstTitle(fixture: String) = Fixtures.lines(fixture).firstNotNullOfOrNull { OutputFiles.titleFromProgressLine(it) }

    // 실제 yt-dlp 출력 줄에서 제목을 미리 알아낸다(조각 파일 줄이 먼저 나와도 같은 제목)
    @Test fun titleFromRealProgressOutput() {
        assertEquals("master", firstTitle("ytdlp_p1080.log"))
        assertEquals("master", firstTitle("ytdlp_audio.log"))
        assertEquals("단일 영상", firstTitle("ytdlp_direct.log"))
    }

    // 조각 파일(형식 ID에 문자 포함)과 임시 파일은 저장 대상이 아니다
    @Test fun fragmentsAndPartialsAreTemporary() {
        assertTrue(OutputFiles.isTemporary("master [master].f5000.mp4"))
        assertTrue(OutputFiles.isTemporary("master [master].faud-Korean.mp4"))
        assertTrue(OutputFiles.isTemporary("a [x].mp4.part"))
        assertTrue(OutputFiles.isTemporary("a [x].f137.mp4.part-Frag3"))
        assertTrue(OutputFiles.isTemporary("a [x].mp4.ytdl"))
        assertFalse(OutputFiles.isTemporary("master [master].mp4"))
        assertFalse(OutputFiles.isTemporary("Project.final [abc].mp4"))
    }

    // 폴더에서 완성 미디어만 큰 순서로, 형식 판별
    @Test fun mediaFilesPickOnlyFinishedMedia() {
        val dir = tmp.newFolder()
        fun write(name: String, size: Int) = java.io.File(dir, name).writeBytes(ByteArray(size))
        write("master [master].mp4", 300)
        write("master [master].f5000.mp4", 900)
        write("extra [b].m4a", 100)
        write("master [master].webp", 50)
        write("x [y].mp4.part", 999)
        write("empty [z].mp4", 0)
        val media = OutputFiles.mediaFiles(dir)
        assertEquals(listOf("master [master].mp4", "extra [b].m4a"), media.map { it.file.name })
        assertEquals(OutputFiles.Kind.VIDEO, media[0].kind); assertEquals("video/mp4", media[0].mimeType)
        assertEquals(OutputFiles.Kind.AUDIO, media[1].kind); assertEquals("audio/mp4", media[1].mimeType)
    }

    // 갤러리 표시 이름: [ID] 제거, 금지 문자 치환, 길이 제한, 확장자 유지
    @Test fun displayNames() {
        assertEquals("단일 영상", OutputFiles.titleFromFileName("단일 영상 [단일 영상].mp4"))
        assertEquals("뮤직비디오 [MV]", OutputFiles.titleFromFileName("뮤직비디오 [MV] [dQw4w9WgXcQ].mp4"))
        assertEquals("a_b_c.mp4", OutputFiles.displayName("a/b:c", "MP4"))
        assertEquals("video.m4a", OutputFiles.displayName("  ...  ", "m4a"))
        assertEquals(124, OutputFiles.displayName("가".repeat(300), "mp4").length)
    }
}
