package com.miu.videograb.engine

/**
 * 화질 선택. yt-dlp의 형식 정렬(-S)로 "이 해상도 이하 중 가장 좋은 것"을 고르고,
 * 휴대폰 갤러리에서 잘 열리도록 mp4(H.264)·m4a를 우선한다.
 */
enum class QualityPreset(val label: String, val isAudioOnly: Boolean) {
    P1080("1080p (기본)", false),
    P720("720p (용량 절약)", false),
    BEST("최고 화질", false),
    AUDIO("소리만 (m4a)", true);

    /** yt-dlp 명령줄 옵션. 엔진 경로·캐시·JS 런타임은 youtubedl-android가 붙인다. */
    fun options(): List<String> = when (this) {
        P1080 -> video("res:1080,ext:mp4:m4a")
        P720 -> video("res:720,ext:mp4:m4a")
        BEST -> video("ext:mp4:m4a")
        AUDIO -> listOf("-f", "ba/b", "-S", "ext:m4a", "-x", "--audio-format", "m4a")
    }

    private fun video(sort: String) = listOf("-f", "bv*+ba/b", "-S", sort, "--merge-output-format", "mp4")

    companion object {
        val DEFAULT = P1080
        fun fromName(name: String?): QualityPreset = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** 작업 하나에 쓰는 전체 yt-dlp 인자. 테스트와 실제 실행이 같은 목록을 쓴다. */
object DownloadCommand {
    /** 재생목록 링크 하나로 너무 많이 받지 않도록 둔 상한(초기 설계값). */
    const val PLAYLIST_LIMIT = 50

    /** 파일 이름 템플릿. 제목이 길면 바이트 기준으로 자르고, 겹치지 않도록 영상 ID를 붙인다. */
    const val OUTPUT_TEMPLATE = "%(title).150B [%(id)s].%(ext)s"

    /** HLS·DASH처럼 조각으로 나뉜 영상을 동시에 받는 연결 수(초기 설계값). */
    const val FRAGMENT_THREADS = 4

    fun options(preset: QualityPreset, outputDir: String): List<String> = buildList {
        addAll(preset.options())
        // 진행률을 한 줄씩 출력해야 진행률·제목 해석이 안정적이다.
        add("--newline")
        addAll(listOf("-N", FRAGMENT_THREADS.toString()))
        // 재생목록 안의 영상 링크는 그 영상 하나만, 재생목록 자체 링크는 앞에서부터 상한까지만.
        add("--no-playlist")
        addAll(listOf("--playlist-end", PLAYLIST_LIMIT.toString()))
        // 갤러리에서 "받은 날짜" 순으로 보이게 원본 수정 시각을 쓰지 않는다.
        add("--no-mtime")
        addAll(listOf("-o", "$outputDir/$OUTPUT_TEMPLATE"))
    }
}
