package com.miu.videograb.engine

import java.net.URI

/** 공유받은 텍스트에서 http/https 링크만 뽑는다. 텍스트는 데이터로만 다루며 다른 스킴은 무시한다. */
object UrlExtractor {
    private val urlRegex = Regex("""https?://[^\s<>"'`]+""", RegexOption.IGNORE_CASE)

    /** 순서를 유지하고 중복은 한 번만. 끝에 붙은 문장부호·닫는 괄호는 뗀다. */
    fun extract(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        return urlRegex.findAll(text)
            .map { trimTrailing(it.value) }
            .filter(::isValid)
            .distinctBy(::normalize)
            .toList()
    }

    private fun trimTrailing(url: String): String {
        var u = url.trimEnd('.', ',', '!', '?', ';', ':', '。', '、')
        // "(https://...)"처럼 괄호로 감싼 경우 짝이 맞지 않는 닫는 괄호만 뗀다.
        while (u.endsWith(")") && u.count { it == '(' } < u.count { it == ')' }) u = u.dropLast(1)
        return u
    }

    private fun isValid(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        return (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
    }

    /** 같은 영상인지 비교하기 위한 키. 대소문자가 의미 없는 스킴·호스트만 소문자로 바꾸고 끝의 '/'를 뗀다. */
    fun normalize(url: String): String {
        val uri = runCatching { URI(url) }.getOrNull() ?: return url.trim()
        val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return url.trim()
        val path = (uri.rawPath ?: "").trimEnd('/')
        val query = uri.rawQuery?.let { "?$it" } ?: ""
        // 쿼리 뒤에 붙은 '/'도 의미 없는 꼬리로 본다(예: ...watch?v=AAA/).
        return "${uri.scheme.lowercase()}://$host$path$query".trimEnd('/')
    }
}
