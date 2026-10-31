package com.miu.lastcontact

import com.miu.lastcontact.domain.ParseResult
import com.miu.lastcontact.importer.ExportReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExportReaderTest {
    private val sample = "엄마 님과 카카오톡 대화\n2026년 10월 1일 오후 3:12, 엄마 : 밥 먹었니\n2026년 10월 2일 오전 9:00, 나 : 네\n"

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z -> entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        return bos.toByteArray()
    }

    @Test fun plainUtf8WithBom() {
        val r = ExportReader.read({ ("﻿" + sample).toByteArray().inputStream() }) as ParseResult.Ok
        assertEquals("엄마", r.summary.chatTitle)
        assertEquals(LocalDate.of(2026, 10, 2), r.summary.lastDay)
    }

    @Test fun legacyCp949File() {
        val bytes = sample.toByteArray(Charset.forName("MS949"))
        val r = ExportReader.read({ bytes.inputStream() }) as ParseResult.Ok
        assertEquals(setOf("엄마", "나"), r.summary.senders.map { it.name }.toSet())
    }

    @Test fun zipWithTxtInside() {
        val z = zip("media/photo.jpg" to ByteArray(10), "KakaoTalkChats.txt" to sample.toByteArray())
        val r = ExportReader.read({ z.inputStream() }) as ParseResult.Ok
        assertEquals(2, r.summary.messageCount)
    }

    @Test fun zipWithoutTxtIsRejected() {
        val z = zip("a.jpg" to ByteArray(10))
        assertTrue(ExportReader.read({ z.inputStream() }) is ParseResult.Error)
    }

    @Test fun zipBombStopsAtTextLimit() {
        // 같은 글자로 가득 찬 텍스트는 압축률이 매우 높다 → 해제 후 크기가 한도를 넘으면 중단
        // (실제 한도 100MB 대신 1MB로 시험, 줄바꿈 없는 거대한 한 줄도 메모리에 통째로 올리지 않음)
        val big = ByteArray(8 * 1024 * 1024) { 'a'.code.toByte() }
        val z = zip("chat.txt" to big)
        assertTrue("압축 크기 ${z.size}", z.size < 100_000)
        val r = ExportReader.read({ z.inputStream() }, maxChars = 1024 * 1024)
        assertTrue(r is ParseResult.Error)
        assertTrue((r as ParseResult.Error).message.contains("너무 큽니다"))
    }

    @Test fun openFailureIsReported() {
        val r = ExportReader.read({ throw java.io.FileNotFoundException("없음") })
        assertTrue(r is ParseResult.Error)
    }
}
