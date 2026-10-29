package com.miu.fold.deals.listings

/** 원문 사양을 정규화한다. 확신할 수 없으면 null(미확인)을 돌려준다. */
object SpecNormalizer {

    data class RamSpec(val totalGb: Int, val moduleGb: Int?, val moduleCount: Int?)

    private val ramModules = Regex("""(\d{1,3})\s*(?:gb|g|기가)?\s*[x×*]\s*(\d{1,2})""", RegexOption.IGNORE_CASE)
    private val sizeWithUnit = Regex("""(\d{1,4}(?:\.\d+)?)\s*(tb|테라|gb|g|기가)?""", RegexOption.IGNORE_CASE)

    /** "16GB" → 16, "16GB×2" → 총 32 / 모듈 16 / 2개. */
    fun parseRam(raw: String?): RamSpec? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        ramModules.find(text)?.let { m ->
            val module = m.groupValues[1].toInt()
            val count = m.groupValues[2].toInt()
            if (module <= 0 || count <= 0) return null
            return RamSpec(module * count, module, count)
        }
        val m = sizeWithUnit.find(text) ?: return null
        val unit = m.groupValues[2].lowercase()
        if (unit == "tb" || unit == "테라") return null
        val value = m.groupValues[1].toDoubleOrNull() ?: return null
        if (value <= 0 || value != Math.floor(value)) return null
        return RamSpec(value.toInt(), null, null)
    }

    /** "512GB" → 512, "1TB" → 1024. 숫자만 있으면 GB로 본다. */
    fun parseStorageGb(raw: String?): Int? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val m = sizeWithUnit.find(text) ?: return null
        val value = m.groupValues[1].toDoubleOrNull() ?: return null
        if (value <= 0) return null
        val unit = m.groupValues[2].lowercase()
        val gb = if (unit == "tb" || unit == "테라") value * 1024 else value
        if (gb != Math.floor(gb)) return null
        return gb.toInt()
    }

    fun formatStorage(gb: Int): String = if (gb >= 1024 && gb % 1024 == 0) "${gb / 1024}TB" else "${gb}GB"

    private val dropTokens = setOf("samsung", "삼성", "galaxy", "갤럭시")

    /**
     * 정확 비교용 모델 키를 만든다.
     * 세대 숫자·파생 이름(SE, Ultra 등)을 지우지 않으므로 Fold SE와 Fold6는 서로 다른 키가 된다.
     */
    fun canonicalModel(raw: String?): String? {
        var s = raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        s = s.replace("폴드", " fold ").replace("플립", " flip ")
            .replace("울트라", " ultra ").replace("프로", " pro ")
        s = s.replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
        s = s.replace(Regex("""\bz\s*(fold|flip)"""), "z $1")
        s = s.replace(Regex("""\b(fold|flip)\s+(\d+)\b"""), "$1$2")
        val tokens = s.split(" ").filter { it.isNotEmpty() && it !in dropTokens }
        if (tokens.isEmpty()) return null
        var key = tokens.joinToString(" ")
        if (key.startsWith("fold") || key.startsWith("flip")) key = "z $key"
        return key
    }
}
