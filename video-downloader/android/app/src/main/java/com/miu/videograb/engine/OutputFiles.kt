package com.miu.videograb.engine

import java.io.File

/** 다운로드가 끝난 작업 폴더에서 저장할 파일을 고르고 갤러리용 이름·형식을 정한다. */
object OutputFiles {
    private val videoExt = setOf("mp4", "mkv", "webm", "mov", "m4v", "3gp")
    private val audioExt = setOf("m4a", "mp3", "opus", "ogg", "aac", "flac", "wav")

    enum class Kind { VIDEO, AUDIO }

    data class Media(val file: File, val kind: Kind, val mimeType: String)

    /** 작업 폴더의 완성된 미디어 파일들. 임시(.part/.ytdl/.temp)와 썸네일·자막은 뺀다. 큰 것부터. */
    fun mediaFiles(dir: File): List<Media> =
        (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && it.length() > 0 && !isTemporary(it.name) }
            .mapNotNull { f -> classify(f)?.let { (kind, mime) -> Media(f, kind, mime) } }
            .sortedByDescending { it.file.length() }

    fun isTemporary(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".part") || n.endsWith(".ytdl") || n.contains(".part-frag") || n.endsWith(".temp") ||
            fragmentSuffix.containsMatchIn(n)
    }

    /**
     * 합치기 전 영상·소리 조각. 형식 ID는 숫자만이 아니다(예: "].f137.mp4", "].faud-Korean.mp4").
     * 템플릿이 항상 "[ID].확장자"로 끝나므로 "]" 바로 뒤의 ".f…"만 조각으로 본다.
     */
    private val fragmentSuffix = Regex("""]\.f[^.\]/]+\.\w+$""")

    fun classify(f: File): Pair<Kind, String>? = when (val ext = f.extension.lowercase()) {
        in videoExt -> Kind.VIDEO to when (ext) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            else -> "video/3gpp"
        }
        in audioExt -> Kind.AUDIO to when (ext) {
            "m4a", "aac" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "opus", "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            else -> "audio/wav"
        }
        else -> null
    }

    /** 템플릿이 붙인 마지막 " [ID]". 사이트에 따라 ID가 한글·공백을 포함하기도 한다. */
    private val idSuffix = Regex("""\s*\[[^\[\]]+]$""")
    private val forbidden = Regex("""[\\/:*?"<>|\u0000-\u001F]""")

    /** 템플릿의 " [영상ID]"를 떼어 사람이 읽을 제목으로. */
    fun titleFromFileName(name: String): String =
        name.substringBeforeLast('.').replace(idSuffix, "").trim().ifEmpty { name }

    /** 갤러리에 쓸 표시 이름. 금지 문자를 바꾸고 너무 길면 자른다(확장자 유지). */
    fun displayName(title: String, ext: String): String {
        val base = title.replace(forbidden, "_").replace(Regex("""\s+"""), " ").trim().trim('.').ifEmpty { "video" }
        return base.take(120) + "." + ext.lowercase()
    }

    /** 진행 중 출력 줄에서 저장 파일 경로를 찾아 제목을 미리 알아낸다. */
    fun titleFromProgressLine(line: String): String? {
        val path = when {
            line.startsWith("[download] Destination: ") -> line.removePrefix("[download] Destination: ")
            line.startsWith("[Merger] Merging formats into \"") -> line.removePrefix("[Merger] Merging formats into \"").removeSuffix("\"")
            line.startsWith("[ExtractAudio] Destination: ") -> line.removePrefix("[ExtractAudio] Destination: ")
            else -> return null
        }
        val name = File(path.trim()).name
        // 조각 파일 이름(.f137.mp4)에서도 제목은 같다.
        return titleFromFileName(name.replace(Regex("""(?<=])\.f[^.\]/]+(?=\.\w+$)"""), "")).takeIf { it.isNotBlank() }
    }
}
