package com.miu.videograb

import com.miu.videograb.engine.UrlExtractor
import org.junit.Assert.assertEquals
import org.junit.Test

class UrlExtractorTest {
    // 유튜브 앱 공유 문구에서 링크만 추출
    @Test fun extractsLinkFromShareText() {
        assertEquals(listOf("https://youtu.be/dQw4w9WgXcQ?si=abc"),
            UrlExtractor.extract("이 영상 보세요! https://youtu.be/dQw4w9WgXcQ?si=abc"))
    }

    // 여러 링크·중복·문장부호·괄호 처리, 순서 유지
    @Test fun multipleLinksDedupAndTrim() {
        val text = """
            1) https://www.youtube.com/watch?v=AAA.
            2) (https://vimeo.com/123)
            3) https://WWW.YouTube.com/watch?v=AAA
            4) https://m.youtube.com/watch?v=AAA/
        """.trimIndent()
        assertEquals(listOf("https://www.youtube.com/watch?v=AAA", "https://vimeo.com/123"), UrlExtractor.extract(text))
    }

    // http/https 외 스킴과 링크 없는 글은 무시
    @Test fun ignoresOtherSchemesAndPlainText() {
        assertEquals(emptyList<String>(), UrlExtractor.extract("javascript:alert(1) file:///sdcard/a.mp4 intent://x"))
        assertEquals(emptyList<String>(), UrlExtractor.extract("그냥 메모"))
        assertEquals(emptyList<String>(), UrlExtractor.extract(null))
    }

    // 쿼리가 다르면 다른 영상으로 본다(같은 키는 스킴·호스트 대소문자, www./m., 끝 슬래시만 무시)
    @Test fun normalizeKeepsQuery() {
        assertEquals(UrlExtractor.normalize("https://m.youtube.com/watch?v=A"), UrlExtractor.normalize("https://www.YOUTUBE.com/watch?v=A"))
        assert(UrlExtractor.normalize("https://youtube.com/watch?v=A") != UrlExtractor.normalize("https://youtube.com/watch?v=B"))
    }
}
