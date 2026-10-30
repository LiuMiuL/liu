package com.miu.videograb

import com.miu.videograb.engine.ErrorClassifier
import com.miu.videograb.engine.ErrorClassifier.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorClassifierTest {
    // 로컬 테스트 서버에서 실제로 받은 yt-dlp 오류
    @Test fun realErrorsFromFixture() {
        val kinds = Fixtures.lines("ytdlp_errors.txt").map(ErrorClassifier::classify)
        assertEquals(listOf(Kind.UNSUPPORTED, Kind.PRIVATE, Kind.NETWORK), kinds)
    }

    // 유튜브 등에서 흔한 오류 문구(yt-dlp 소스의 메시지 형식)
    @Test fun commonSiteErrors() {
        assertEquals(Kind.PRIVATE, ErrorClassifier.classify("ERROR: [youtube] abc: Private video. Sign in if you've been granted access to this video"))
        assertEquals(Kind.LOGIN_REQUIRED, ErrorClassifier.classify("ERROR: [youtube] abc: Sign in to confirm your age. This video may be inappropriate for some users."))
        assertEquals(Kind.BLOCKED, ErrorClassifier.classify("ERROR: [youtube] abc: Sign in to confirm you’re not a bot. Use --cookies-from-browser"))
        assertEquals(Kind.DRM, ErrorClassifier.classify("ERROR: [someSite] abc: This video is DRM protected"))
        assertEquals(Kind.GEO, ErrorClassifier.classify("ERROR: [x] The uploader has not made this video available in your country"))
        assertEquals(Kind.EXTRACTOR, ErrorClassifier.classify("ERROR: [youtube] abc: Unable to extract nsig function code; please report this issue"))
        assertEquals(Kind.STORAGE, ErrorClassifier.classify("OSError: [Errno 28] No space left on device"))
        assertEquals(Kind.UNKNOWN, ErrorClassifier.classify(null))
    }

    // 엔진 업데이트 권유는 사이트 변경·차단 계열만
    @Test fun updateSuggestion() {
        assertTrue(Kind.EXTRACTOR.suggestEngineUpdate && Kind.BLOCKED.suggestEngineUpdate)
        assertTrue(Kind.entries.filter { it.suggestEngineUpdate }.toSet() == setOf(Kind.EXTRACTOR, Kind.BLOCKED))
    }

    // 상세 보기에는 ERROR 줄만 남긴다
    @Test fun summarizeKeepsErrorLines() {
        val text = "[info] foo\n[download] 3%\nERROR: Unsupported URL: http://x\n"
        assertEquals("ERROR: Unsupported URL: http://x", ErrorClassifier.summarize(text))
    }
}
