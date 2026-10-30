package com.miu.videograb.engine

/** yt-dlp 오류 출력을 사용자가 이해할 수 있는 한국어 안내로 바꾼다. 원문은 따로 보관한다. */
object ErrorClassifier {
    enum class Kind(val message: String, val retryable: Boolean, val suggestEngineUpdate: Boolean = false) {
        UNSUPPORTED("지원하지 않는 링크예요. 영상 페이지 주소인지 확인해 주세요.", false),
        PRIVATE("비공개이거나 삭제된 영상이에요.", false),
        LOGIN_REQUIRED("로그인·연령 확인이 필요한 영상이라 받을 수 없어요.", false),
        DRM("저작권 보호(DRM)가 걸린 영상은 받을 수 없어요.", false),
        GEO("이 지역에서는 볼 수 없는 영상이에요.", false),
        LIVE("진행 중인 라이브 방송은 끝난 뒤에 받을 수 있어요.", true),
        NETWORK("인터넷 연결이 불안정해요. 연결을 확인하고 다시 시도해 주세요.", true),
        STORAGE("기기 저장 공간이 부족해요.", true),
        BLOCKED("사이트가 요청을 막았어요. 잠시 뒤 다시 시도하거나 엔진을 업데이트해 보세요.", true, true),
        EXTRACTOR("사이트 구조가 바뀌어 읽지 못했어요. 설정에서 다운로드 엔진을 업데이트해 보세요.", true, true),
        UNKNOWN("다운로드하지 못했어요.", true),
    }

    fun classify(errorText: String?): Kind {
        val e = errorText?.lowercase() ?: return Kind.UNKNOWN
        return when {
            "drm" in e -> Kind.DRM
            "unsupported url" in e || "is not a valid url" in e -> Kind.UNSUPPORTED
            // 봇 확인 문구에도 "--cookies"가 들어 있으므로 로그인 판정보다 먼저 본다.
            "not a bot" in e -> Kind.BLOCKED
            "private video" in e || "video unavailable" in e || "has been removed" in e || "http error 404" in e -> Kind.PRIVATE
            "sign in to confirm your age" in e || "age-restricted" in e || "login required" in e ||
                "requires authentication" in e || "members-only" in e || "--cookies" in e -> Kind.LOGIN_REQUIRED
            "available in your country" in e || "geo restrict" in e || "geo-restrict" in e -> Kind.GEO
            "is live" in e || "live event will begin" in e || "premieres in" in e -> Kind.LIVE
            "no space left" in e || "enospc" in e -> Kind.STORAGE
            "sign in to confirm you" in e || "http error 403" in e || "http error 429" in e || "too many requests" in e -> Kind.BLOCKED
            "unable to extract" in e || "unable to download webpage" in e && "extract" in e || "nsig" in e ||
                "signature" in e || "please report this issue" in e -> Kind.EXTRACTOR
            "unable to download" in e || "timed out" in e || "connection" in e || "network is unreachable" in e ||
                "temporary failure in name resolution" in e || "errno" in e -> Kind.NETWORK
            else -> Kind.UNKNOWN
        }
    }

    /** 오류 원문에서 "ERROR:" 줄만 짧게 남긴다(상세 보기용). */
    fun summarize(errorText: String?, max: Int = 600): String? {
        val text = errorText?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val errLines = text.lines().filter { it.contains("ERROR", ignoreCase = false) }
        return (errLines.ifEmpty { text.lines().takeLast(5) }).joinToString("\n").take(max)
    }
}
