package com.miu.fold.dashboard.shortcuts

import java.net.URI

enum class ShortcutType { WEB, APP, INTERNAL }

/** 앱 안 목적지. 외부 입력으로 임의 동작을 만들지 않도록 고정 목록만 허용한다. */
enum class InternalTarget(val key: String, val label: String) {
    NEW_NOTE("new_note", "새 메모"),
    TODOS("todos", "할 일"),
    TIMER("timer", "집중 타이머"),
    CALENDAR("calendar", "일정"),
    SEARCH("search", "검색");

    companion object {
        fun fromKey(key: String): InternalTarget? = entries.firstOrNull { it.key == key }
    }
}

sealed interface UrlCheck {
    data class Ok(val url: String, val insecure: Boolean) : UrlCheck
    data class Rejected(val reason: String) : UrlCheck
}

object UrlValidator {
    private val secretParams = setOf(
        "token", "access_token", "id_token", "refresh_token", "api_key", "apikey", "key",
        "secret", "client_secret", "password", "passwd", "pwd", "auth", "authorization", "session", "sig", "signature",
    )
    const val MAX_LENGTH = 2048

    /**
     * 웹 바로가기 URL 검사.
     * - 스킴이 없으면 https:// 를 붙인다.
     * - https 기본, http는 경고와 함께 허용, 그 외(javascript:, intent:, file:, content:, data: …)는 거절.
     * - 사용자 정보(user:pass@)와 토큰·키로 보이는 쿼리 파라미터는 저장을 거절한다.
     */
    fun check(raw: String): UrlCheck {
        val input = raw.trim()
        if (input.isEmpty()) return UrlCheck.Rejected("주소를 입력하세요.")
        if (input.length > MAX_LENGTH) return UrlCheck.Rejected("주소가 너무 깁니다.")
        if (input.any { it.isWhitespace() || it.code < 0x20 }) return UrlCheck.Rejected("주소에 공백이나 제어 문자가 있습니다.")
        val withScheme = when {
            Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(input) -> input
            // "example.com:8080/path" 같은 호스트:포트는 스킴으로 보지 않는다.
            Regex("^[^:/]+\\.[^:/]+:\\d+(/.*)?$").matches(input) -> "https://$input"
            // javascript:, intent:, data:, mailto: 처럼 // 없는 스킴은 그대로 두어 아래에서 거절한다.
            Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(input) -> input
            else -> "https://$input"
        }
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return UrlCheck.Rejected("올바른 주소 형식이 아닙니다.")
        val scheme = uri.scheme?.lowercase() ?: return UrlCheck.Rejected("올바른 주소 형식이 아닙니다.")
        if (scheme != "https" && scheme != "http") return UrlCheck.Rejected("지원하지 않는 주소 종류입니다($scheme:). https 주소만 사용할 수 있습니다.")
        val host = uri.host
        if (host.isNullOrBlank()) return UrlCheck.Rejected("주소에 도메인이 없습니다.")
        if (uri.rawUserInfo != null) return UrlCheck.Rejected("주소에 계정 정보(아이디·비밀번호)를 넣을 수 없습니다.")
        val query = uri.rawQuery
        if (query != null) {
            val names = query.split('&').map { it.substringBefore('=').lowercase() }
            val bad = names.firstOrNull { it in secretParams }
            if (bad != null) return UrlCheck.Rejected("인증 토큰·키로 보이는 값($bad)은 바로가기에 저장하지 않습니다.")
        }
        return UrlCheck.Ok(uri.toString(), insecure = scheme == "http")
    }
}

object PackageNameRule {
    private val regex = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
    fun isValid(pkg: String): Boolean = pkg.length <= 255 && regex.matches(pkg)
}
