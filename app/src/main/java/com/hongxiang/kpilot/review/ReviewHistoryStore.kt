package com.hongxiang.kpilot.review

import com.hongxiang.kpilot.notes.AtomicCache
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant

/** Versioned private binary storage; never stores connection credentials or clipping text. */
class ReviewHistoryStore(private val file: File) {
    fun load(): ReviewSnapshot = if (file.isFile) file.inputStream().use(::read) else ReviewSnapshot()

    fun save(snapshot: ReviewSnapshot) {
        AtomicCache.writeValidated(file, { write(snapshot, it) }, { candidate ->
            check(candidate.inputStream().use(::read) == snapshot) { "Review history validation failed" }
        })
    }

    private fun write(snapshot: ReviewSnapshot, stream: OutputStream) {
        val output = DataOutputStream(stream)
        output.writeInt(MAGIC)
        output.writeInt(VERSION)
        output.writeInt(snapshot.records.size)
        snapshot.records.toSortedMap().forEach { (id, record) ->
            output.writeUTF(id)
            output.writeInt(record.reviewCount)
            output.writeBoolean(record.lastReviewedAt != null)
            record.lastReviewedAt?.let { output.writeLong(it.epochSecond); output.writeInt(it.nano) }
            output.writeBoolean(record.favorite)
            output.writeBoolean(record.excluded)
        }
        writeIds(output, snapshot.deck)
        writeIds(output, snapshot.cycleIds.sorted())
        writeIds(output, snapshot.batch)
        output.writeInt(snapshot.batchTotal)
        writeIds(output, snapshot.displayedIds.sorted())
        output.writeInt(snapshot.dailyCounts.size)
        snapshot.dailyCounts.toSortedMap().forEach { (date, count) -> output.writeUTF(date); output.writeInt(count) }
        output.flush()
    }

    private fun read(stream: InputStream): ReviewSnapshot {
        val input = DataInputStream(stream)
        check(input.readInt() == MAGIC && input.readInt() == VERSION) { "Unsupported review history" }
        val records = linkedMapOf<String, ReviewRecord>()
        repeat(readCount(input)) {
            val id = input.readUTF()
            val count = input.readInt().also { check(it >= 0) }
            val reviewed = if (input.readBoolean()) Instant.ofEpochSecond(input.readLong(), input.readInt().toLong()) else null
            check(id.isNotBlank() && id !in records)
            records[id] = ReviewRecord(count, reviewed, input.readBoolean(), input.readBoolean())
        }
        val deck = readIds(input)
        val cycle = readIds(input).toSet()
        val batch = readIds(input)
        val batchTotal = input.readInt().also { check(it in 0..ReviewPlanner.BATCH_SIZE) }
        val displayed = readIds(input).toSet()
        val counts = linkedMapOf<String, Int>()
        repeat(readCount(input)) {
            val day = input.readUTF().also { check(it.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) }
            counts[day] = input.readInt().also { check(it >= 0) }
        }
        check(input.read() == -1 && batch.size <= batchTotal && deck.containsAll(batch)) { "Invalid review history" }
        return ReviewSnapshot(records, deck, cycle, batch, batchTotal, displayed, counts)
    }

    private fun writeIds(output: DataOutputStream, ids: Collection<String>) {
        output.writeInt(ids.size)
        ids.forEach(output::writeUTF)
    }

    private fun readIds(input: DataInputStream): List<String> = List(readCount(input)) { input.readUTF().also { check(it.isNotBlank()) } }
        .also { check(it.size == it.distinct().size) }

    private fun readCount(input: DataInputStream): Int = input.readInt().also { check(it in 0..1_000_000) }

    private companion object {
        const val MAGIC = 0x4B505256
        const val VERSION = 1
    }
}
