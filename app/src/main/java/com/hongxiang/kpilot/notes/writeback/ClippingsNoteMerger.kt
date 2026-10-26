package com.hongxiang.kpilot.notes.writeback

import com.hongxiang.kpilot.notes.ClippingsTextDecoder
import com.hongxiang.kpilot.notes.ClippingsValidation
import com.hongxiang.kpilot.notes.KindleClipping
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object ClippingsNoteMerger {
    private val separator = Regex("(?m)^={10,}\\s*$")
    private fun normalize(value: String) = value.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')

    fun merge(original: ByteArray, clipping: KindleClipping, text: String, note: SidecarAnnotation?, kfx: Boolean): ByteArray {
        val (parsed, issue) = ClippingsValidation.inspect(original)
        require(issue == null) { "Kindle 的 My Clippings.txt 格式不完整，已停止写入" }
        val fresh = parsed.items.firstOrNull { it.id == clipping.id }
            ?: error("Kindle 的 My Clippings.txt 中已找不到这条标注")
        val originalText = ClippingsTextDecoder.decode(original)
        val source = normalize(originalText)
        val records = source.split(separator).filter { it.isNotBlank() }.map { it.trim() }.toMutableList()
        val toRemove = if (text.isEmpty()) fresh.kindleNotes.map { normalize(it.raw).trim() }.toSet()
            else setOfNotNull(fresh.kindleNotes.lastOrNull()?.raw?.let { normalize(it).trim() })
        records.removeAll(toRemove)
        if (text.isNotEmpty()) {
            require(note != null) { "Kindle 笔记数据缺失" }
            val start = if (kfx) fresh.locationEnd ?: fresh.locationStart else fresh.locationStart ?: fresh.locationEnd
            val end = if (kfx) start else fresh.locationEnd ?: start
            require(start != null) { "标注缺少位置" }
            val location = if (start == end) "$start" else "$start-$end"
            val title = fresh.bookTitle + fresh.author?.let { " ($it)" }.orEmpty()
            val date = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy h:mm:ss a", Locale.US)
                .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(note.creationTime))
            records += "$title\n- Your Note on Location $location | Added on $date\n\n$text"
        }
        val updated = if (records.isEmpty()) "" else records.joinToString("\n==========\n") + "\n==========\n"
        val lineEnding = when { originalText.contains("\r\n") -> "\r\n"; originalText.contains('\r') -> "\r"; else -> "\n" }
        val bytes = encodeLike(original, if (lineEnding == "\n") updated else updated.replace("\n", lineEnding))
        val (verified, error) = ClippingsValidation.inspect(bytes)
        require(error == null) { "更新后的 My Clippings.txt 无法解析" }
        val result = verified.items.firstOrNull { it.id == clipping.id } ?: error("更新后丢失了原标注")
        require(if (text.isEmpty()) result.kindleNotes.isEmpty() else result.kindleNotes.any { it.text == text }) {
            "更新后的 My Clippings.txt 笔记校验失败"
        }
        return bytes
    }

    private fun encodeLike(original: ByteArray, text: String): ByteArray {
        fun has(vararg prefix: Int) = original.size >= prefix.size && prefix.indices.all { original[it] == prefix[it].toByte() }
        return when {
            has(0xFF, 0xFE) -> byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
            has(0xFE, 0xFF) -> byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + text.toByteArray(Charsets.UTF_16BE)
            has(0xEF, 0xBB, 0xBF) -> byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray(Charsets.UTF_8)
            else -> {
                // Match the same no-BOM UTF-16 detection used when reading clippings.
                val sample = original.take(256)
                val evenZeros = sample.withIndex().count { it.index % 2 == 0 && it.value == 0.toByte() }
                val oddZeros = sample.withIndex().count { it.index % 2 == 1 && it.value == 0.toByte() }
                val charset = when {
                    oddZeros > sample.size / 8 && oddZeros > evenZeros * 2 -> Charsets.UTF_16LE
                    evenZeros > sample.size / 8 && evenZeros > oddZeros * 2 -> Charsets.UTF_16BE
                    else -> Charsets.UTF_8
                }
                text.toByteArray(charset)
            }
        }
    }
}
