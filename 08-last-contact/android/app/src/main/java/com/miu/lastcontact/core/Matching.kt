package com.miu.lastcontact.core

import java.util.UUID

object Ids {
    fun newId(): String = UUID.randomUUID().toString()
}

/** 카톡 이름·사람 이름 비교용: 공백 제거, 대소문자 무시, 유니코드 정규화. */
object Names {
    fun key(name: String): String =
        java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFC)
            .filterNot { it.isWhitespace() || it == '​' }
            .lowercase()

    /** 줄바꿈·쉼표로 구분한 별칭 문자열 → 목록 */
    fun splitAliases(raw: String): List<String> =
        raw.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    fun joinAliases(list: List<String>): String = list.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n")
}

/** 전화번호 비교용: 숫자만, 국가번호 82는 0으로. */
object Phones {
    fun normalize(raw: String?): String? {
        if (raw == null) return null
        var d = raw.filter { it.isDigit() }
        if (d.startsWith("0082")) d = "0" + d.removePrefix("0082")
        else if (d.startsWith("82") && d.length >= 11) d = "0" + d.removePrefix("82")
        return d.takeIf { it.length >= 7 }
    }

    fun split(raw: String): List<String> = raw.split(',', '\n').mapNotNull { normalize(it) }.distinct()

    /** 010-1234-5678 형식으로 보여주기(번호 저장은 숫자만). */
    fun display(n: String): String = when {
        n.length == 11 && n.startsWith("010") -> "${n.substring(0, 3)}-${n.substring(3, 7)}-${n.substring(7)}"
        n.length == 10 && n.startsWith("02") -> "${n.substring(0, 2)}-${n.substring(2, 6)}-${n.substring(6)}"
        n.length == 10 -> "${n.substring(0, 3)}-${n.substring(3, 6)}-${n.substring(6)}"
        n.length == 9 && n.startsWith("02") -> "${n.substring(0, 2)}-${n.substring(2, 5)}-${n.substring(5)}"
        else -> n
    }
}
