package com.hongxiang.kpilot.notes.writeback

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Lossless KRDS subset used by native AZW3, MOBI and KFX annotation sidecars. */
internal sealed interface KrdsValue {
    data class Integer(val value: Int) : KrdsValue
    data class LongValue(val value: Long) : KrdsValue
    data class Utf(val value: String, val emptyMarker: Boolean = false) : KrdsValue
    data class ObjectValue(val name: String, val values: MutableList<KrdsValue>) : KrdsValue
    // Keep raw IEEE bits (including NaN payloads) and all otherwise unmodified scalars.
    data class Scalar(val tag: Int, val bytes: ByteArray) : KrdsValue
}

internal data class SidecarAnnotation(
    val type: Int,
    val startPosition: String,
    val endPosition: String,
    val creationTime: Long,
    val modificationTime: Long,
    val template: String,
    val note: String?
) {
    val locationStart: Int? get() = location(startPosition)
    val locationEnd: Int? get() = location(endPosition)

    private fun location(position: String): Int? = position.substringBefore(':').toLongOrNull()
        ?.takeIf { it >= 0 && it / 150 < Int.MAX_VALUE }?.let { (it / 150).toInt() + 1 }
}

internal class KindleReaderDataStore(val values: MutableList<KrdsValue>) {
    fun encoded(): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.write(SIGNATURE)
            writeValue(output, KrdsValue.LongValue(1))
            writeValue(output, KrdsValue.Integer(values.size))
            values.forEach { writeValue(output, it) }
        }
        return bytes.toByteArray()
    }

    fun annotations(): List<SidecarAnnotation> {
        val cache = cache() ?: return emptyList()
        val result = mutableListOf<SidecarAnnotation>()
        groups(cache).forEach { (type, tree) ->
            val count = tree.values.first().integer()
            require(count in 0 until tree.values.size) { "Kindle 标注对象数量不匹配" }
            for (index in 1..count) result += decodeAnnotation(type, tree.values[index].objectValue())
        }
        return result
    }

    /** A null result denotes clearing notes; the original highlight and other fields survive. */
    fun updateNote(
        highlight: SidecarAnnotation,
        existingTexts: Set<String>,
        text: String,
        time: Long,
        atHighlightEnd: Boolean
    ): SidecarAnnotation? {
        val cache = cache() ?: error("Kindle sidecar 没有标注缓存")
        val groups = groups(cache)
        val noteTree = groups.firstOrNull { it.first == NOTE }?.second
        val start = if (atHighlightEnd) highlight.endPosition else highlight.startPosition
        val end = highlight.endPosition
        val matches = noteTree?.let { tree ->
            val count = tree.values.first().integer()
            require(count in 0 until tree.values.size) { "Kindle 笔记数量无效" }
            (1..count).filter { index ->
                val note = decodeAnnotation(NOTE, tree.values[index].objectValue())
                (note.startPosition == start && note.endPosition == end) ||
                    (note.note in existingTexts && overlaps(note, highlight))
            }
        }.orEmpty()
        if (text.isEmpty()) {
            if (noteTree != null) {
                matches.asReversed().forEach { noteTree.values.removeAt(it) }
                noteTree.values[0] = KrdsValue.Integer(noteTree.values[0].integer() - matches.size)
            }
            return null
        }
        val preferred = matches.lastOrNull { index ->
            decodeAnnotation(NOTE, noteTree!!.values[index].objectValue()).note in existingTexts
        } ?: matches.lastOrNull()
        if (preferred != null && noteTree != null) {
            val note = noteTree.values[preferred].objectValue()
            require(note.values.size >= 6) { "Kindle 笔记字段不足" }
            note.values[3] = KrdsValue.LongValue(time)
            note.values[5] = KrdsValue.Utf(text)
            return decodeAnnotation(NOTE, note)
        }
        val note = KrdsValue.ObjectValue("annotation.personal.note", mutableListOf(
            KrdsValue.Utf(start), KrdsValue.Utf(end), KrdsValue.LongValue(time),
            KrdsValue.LongValue(time), KrdsValue.Utf(highlight.template), KrdsValue.Utf(text)
        ))
        if (noteTree != null) {
            val count = noteTree.values[0].integer()
            noteTree.values.add(count + 1, note)
            noteTree.values[0] = KrdsValue.Integer(count + 1)
        } else {
            cache.values[0] = KrdsValue.Integer(cache.values[0].integer() + 1)
            cache.values.addAll(1 + groups.size * 2, listOf(KrdsValue.Integer(NOTE),
                KrdsValue.ObjectValue("saved.avl.interval.tree", mutableListOf(KrdsValue.Integer(1), note))))
        }
        return decodeAnnotation(NOTE, note)
    }

    private fun cache() = values.filterIsInstance<KrdsValue.ObjectValue>()
        .firstOrNull { it.name == "annotation.cache.object" }

    private fun groups(cache: KrdsValue.ObjectValue): List<Pair<Int, KrdsValue.ObjectValue>> {
        val count = cache.values.firstOrNull()?.integer() ?: error("Kindle 标注缓存为空")
        require(count >= 0 && count <= (cache.values.size - 1) / 2) { "Kindle 标注分组无效" }
        return (0 until count).map { index ->
            val tree = cache.values[index * 2 + 2].objectValue()
            require(tree.name == "saved.avl.interval.tree" && tree.values.isNotEmpty()) { "Kindle 标注树无效" }
            cache.values[index * 2 + 1].integer() to tree
        }
    }

    companion object {
        const val HIGHLIGHT = 1
        const val NOTE = 2
        private val SIGNATURE = byteArrayOf(0, 0, 0, 0, 0, 0x1A, 0xB1.toByte(), 0x26)
        private const val MAX_DEPTH = 128

        fun decode(bytes: ByteArray): KindleReaderDataStore {
            val input = DataInputStream(ByteArrayInputStream(bytes))
            val signature = ByteArray(SIGNATURE.size).also(input::readFully)
            require(signature.contentEquals(SIGNATURE)) { "不支持的 Kindle sidecar 格式" }
            require(readValue(input, 0) == KrdsValue.LongValue(1)) { "不支持的 Kindle sidecar 版本" }
            val count = readValue(input, 0).integer()
            require(count >= 0 && count <= input.available()) { "Kindle sidecar 对象数量无效" }
            val values = MutableList(count) { readValue(input, 0) }
            require(input.available() == 0) { "Kindle sidecar 尾部存在未解析数据" }
            return KindleReaderDataStore(values)
        }

        private fun readValue(input: DataInputStream, depth: Int): KrdsValue {
            require(depth < MAX_DEPTH) { "Kindle sidecar 嵌套过深" }
            val tag = input.readByte().toInt()
            return when (tag) {
                1 -> KrdsValue.Integer(input.readInt())
                2 -> KrdsValue.LongValue(input.readLong())
                3 -> readUtf(input)
                -2 -> {
                    val name = readUtf(input).value
                    val children = mutableListOf<KrdsValue>()
                    while (true) {
                        input.mark(1)
                        if (input.readByte().toInt() == -1) break
                        input.reset()
                        children += readValue(input, depth + 1)
                    }
                    KrdsValue.ObjectValue(name, children)
                }
                0, 4, 5, 6, 7, 9 -> {
                    val size = when (tag) { 4 -> 8; 5 -> 2; 6 -> 4; else -> 1 }
                    val raw = ByteArray(size).also(input::readFully)
                    require(tag != 0 || raw[0].toInt() in 0..1) { "Kindle sidecar 布尔值无效" }
                    KrdsValue.Scalar(tag, raw)
                }
                else -> error("Kindle sidecar 包含不支持的数据类型：$tag")
            }
        }

        private fun readUtf(input: DataInputStream): KrdsValue.Utf = when (input.readByte().toInt()) {
            1 -> KrdsValue.Utf("", true)
            0 -> {
                val bytes = ByteArray(input.readUnsignedShort()).also(input::readFully)
                val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                KrdsValue.Utf(decoder.decode(ByteBuffer.wrap(bytes)).toString())
            }
            else -> error("Kindle sidecar 字符串标记无效")
        }

        private fun writeValue(output: DataOutputStream, value: KrdsValue) {
            when (value) {
                is KrdsValue.Integer -> { output.writeByte(1); output.writeInt(value.value) }
                is KrdsValue.LongValue -> { output.writeByte(2); output.writeLong(value.value) }
                is KrdsValue.Utf -> { output.writeByte(3); writeUtf(output, value) }
                is KrdsValue.Scalar -> { output.writeByte(value.tag); output.write(value.bytes) }
                is KrdsValue.ObjectValue -> {
                    output.writeByte(-2)
                    writeUtf(output, KrdsValue.Utf(value.name))
                    value.values.forEach { writeValue(output, it) }
                    output.writeByte(-1)
                }
            }
        }

        private fun writeUtf(output: DataOutputStream, value: KrdsValue.Utf) {
            if (value.emptyMarker) {
                require(value.value.isEmpty()) { "非空字符串不能使用空标记" }
                output.writeByte(1)
            } else {
                val bytes = value.value.toByteArray(Charsets.UTF_8)
                require(bytes.size <= 65535) { "批注内容过长（UTF-8 最多 65535 字节）" }
                output.writeByte(0)
                output.writeShort(bytes.size)
                output.write(bytes)
            }
        }

        private fun decodeAnnotation(type: Int, value: KrdsValue.ObjectValue): SidecarAnnotation {
            require(value.values.size >= if (type == NOTE) 6 else 5) { "Kindle 标注字段不足" }
            val fields = value.values
            return SidecarAnnotation(type, fields[0].string(), fields[1].string(), fields[2].long(),
                fields[3].long(), fields[4].string(), if (type == NOTE) fields[5].string() else null)
        }

        fun overlaps(left: SidecarAnnotation, right: SidecarAnnotation): Boolean {
            val a = left.locationStart
            val b = right.locationStart
            if (a != null && b != null) return a <= (right.locationEnd ?: b) && (left.locationEnd ?: a) >= b
            return setOf(left.startPosition, left.endPosition).intersect(setOf(right.startPosition, right.endPosition)).isNotEmpty()
        }
    }
}

private fun KrdsValue.integer() = (this as? KrdsValue.Integer)?.value ?: error("Kindle sidecar 需要整数")
private fun KrdsValue.long() = (this as? KrdsValue.LongValue)?.value ?: error("Kindle sidecar 需要长整数")
private fun KrdsValue.string() = (this as? KrdsValue.Utf)?.value ?: error("Kindle sidecar 需要字符串")
private fun KrdsValue.objectValue() = this as? KrdsValue.ObjectValue ?: error("Kindle sidecar 需要对象")
