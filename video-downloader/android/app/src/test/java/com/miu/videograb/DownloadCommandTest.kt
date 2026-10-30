package com.miu.videograb

import com.miu.videograb.engine.DownloadCommand
import com.miu.videograb.engine.QualityPreset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 이 목록들은 데스크톱 yt-dlp 2026.10.26 + 로컬 HLS(1080p·720p 영상 + 별도 소리)로 실제 실행해 확인한 값이다
 * (docs/TEST_REPORT.md). 바꾸면 그 확인도 다시 해야 한다.
 */
class DownloadCommandTest {
    private val tail = listOf("--newline", "-N", "4", "--no-playlist", "--playlist-end", "50", "--no-mtime", "-o", "/out/%(title).150B [%(id)s].%(ext)s")

    @Test fun p1080MergesBestUpTo1080IntoMp4() = assertEquals(
        listOf("-f", "bv*+ba/b", "-S", "res:1080,ext:mp4:m4a", "--merge-output-format", "mp4") + tail,
        DownloadCommand.options(QualityPreset.P1080, "/out"),
    )

    @Test fun p720LimitsResolution() = assertEquals(
        listOf("-f", "bv*+ba/b", "-S", "res:720,ext:mp4:m4a", "--merge-output-format", "mp4") + tail,
        DownloadCommand.options(QualityPreset.P720, "/out"),
    )

    @Test fun bestHasNoResolutionCap() = assertEquals(
        listOf("-f", "bv*+ba/b", "-S", "ext:mp4:m4a", "--merge-output-format", "mp4") + tail,
        DownloadCommand.options(QualityPreset.BEST, "/out"),
    )

    @Test fun audioExtractsM4a() = assertEquals(
        listOf("-f", "ba/b", "-S", "ext:m4a", "-x", "--audio-format", "m4a") + tail,
        DownloadCommand.options(QualityPreset.AUDIO, "/out"),
    )

    @Test fun unknownPresetFallsBackToDefault() {
        assertEquals(QualityPreset.P1080, QualityPreset.fromName("SOMETHING_OLD"))
        assertEquals(QualityPreset.P1080, QualityPreset.fromName(null))
    }
}
