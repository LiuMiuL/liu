package com.miu.lastcontact.importer

import com.miu.lastcontact.domain.KakaoExportParser
import com.miu.lastcontact.domain.ParseResult
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

/**
 * 카톡 내보내기 파일(.txt 또는 .txt가 든 .zip)을 읽어 해석한다. 파일을 앱에 저장하지 않고 스트림에서 바로 읽는다.
 * - ZIP: .txt 항목만, 항목 수 200개·파일 200MB·해제한 텍스트 100MB(해석기 한도) 제한(압축 폭탄 방지). 파일을 풀어 쓰지 않으므로 경로 조작 영향 없음
 * - 문자셋: UTF-8로 읽어 형식이 안 맞으면 옛 PC 내보내기용 MS949(CP949)로 한 번 더
 */
object ExportReader {
    const val MAX_TOTAL_BYTES = 200L * 1024 * 1024
    const val MAX_ENTRIES = 200

    fun read(open: () -> InputStream, maxChars: Long = KakaoExportParser.MAX_CHARS): ParseResult {
        val first = runCatching { readWith(open, Charsets.UTF_8, maxChars) }.getOrElse { return ParseResult.Error(it.message ?: "파일을 읽지 못했습니다.") }
        if (first is ParseResult.Ok) return first
        val second = runCatching { readWith(open, Charset.forName("MS949"), maxChars) }.getOrNull()
        return if (second is ParseResult.Ok) second else first
    }

    private fun readWith(open: () -> InputStream, cs: Charset, maxChars: Long): ParseResult = open().use { raw ->
        val input = BufferedInputStream(Limited(raw, MAX_TOTAL_BYTES))
        input.mark(4)
        val magic = ByteArray(2)
        val n = input.read(magic)
        input.reset()
        if (n == 2 && magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()) readZip(input, cs, maxChars)
        else KakaoExportParser.parse(input.bufferedReader(cs), maxChars)
    }

    private fun readZip(input: InputStream, cs: Charset, maxChars: Long): ParseResult {
        val zip = ZipInputStream(input)
        var entries = 0
        while (true) {
            val e = zip.nextEntry ?: break
            if (++entries > MAX_ENTRIES) return ParseResult.Error("ZIP 안의 파일이 너무 많습니다.")
            if (e.isDirectory || !e.name.lowercase().endsWith(".txt")) continue
            // ZipInputStream은 항목 끝에서 -1을 돌려주므로 닫지 않고 그 항목만 읽는다.
            val reader = object : FilterInputStream(zip) { override fun close() = Unit }.bufferedReader(cs)
            return KakaoExportParser.parse(reader, maxChars)
        }
        return ParseResult.Error("ZIP 안에 카카오톡 대화 텍스트(.txt)가 없습니다.")
    }

    /** 읽은 총 바이트가 한도를 넘으면 중단(ZIP 해제 크기 포함). */
    private class Limited(inner: InputStream, private val max: Long) : FilterInputStream(inner) {
        private var count = 0L
        private fun add(n: Int): Int { if (n > 0) { count += n; if (count > max) throw IOException("파일이 너무 큽니다(200MB 초과).") }; return n }
        override fun read(): Int = super.read().also { if (it >= 0) add(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = add(super.read(b, off, len))
    }
}
