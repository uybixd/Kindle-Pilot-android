package com.hongxiang.kpilot.library

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class MobiCoverExtractorTest {
    private val cover = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4)
    private val thumbnail = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 5, 6, 7, 8)
    private val recordZero = 102
    private val mobiStart = recordZero + 16
    private val exthStart = mobiStart + 232

    @Test fun extractsCoverBeforeThumbnail() {
        assertArrayEquals(cover, fixture().let(MobiCoverExtractor::cover)?.data)
    }

    @Test fun fallsBackToThumbnailForInvalidCoverIndex() {
        val data = fixture()
        put(0xFFFF_FFFFL, data, exthStart + 20)
        assertArrayEquals(thumbnail, MobiCoverExtractor.cover(data)?.data)
    }

    @Test fun rejectsOverflowingImageIndexWithoutCrashing() {
        val data = fixture()
        put(0xFFFF_FFFFL, data, mobiStart + 92)
        assertNull(MobiCoverExtractor.cover(data))
    }

    @Test fun rejectsTruncatedAndMalformedHeaders() {
        val data = fixture()
        for (length in listOf(0, 76, 90, 120, 250, exthStart + 10)) {
            assertNull("length $length", MobiCoverExtractor.cover(data.copyOf(length)))
        }
        val invalidLength = data.copyOf()
        put(0xFFFF_FFFFL, invalidLength, exthStart + 4)
        assertNull(MobiCoverExtractor.cover(invalidLength))
        val invalidCount = data.copyOf()
        put(0xFFFF_FFFFL, invalidCount, exthStart + 8)
        assertNull(MobiCoverExtractor.cover(invalidCount))
    }

    @Test fun rejectsReversedRecordOffsets() {
        val data = fixture()
        put(101, data, 86)
        assertNull(MobiCoverExtractor.cover(data))
    }

    @Test fun acceptsPngAndGifCovers() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2)
        val pngFile = fixture(coverBytes = png)
        assertArrayEquals(png, MobiCoverExtractor.cover(pngFile)?.data)
        val gif = "GIF89a".toByteArray(Charsets.US_ASCII) + byteArrayOf(1, 2, 3)
        val gifFile = fixture(coverBytes = gif)
        assertArrayEquals(gif, MobiCoverExtractor.cover(gifFile)?.data)
    }

    @Test fun extractsKindleThumbnailIdentityWithoutChangingBook() {
        val data = fixture(contentId = "1741a2f3-0bcf-4c2c-a6db-b90ab9623427", contentType = "EBOK")
        val result = MobiCoverExtractor.cover(data)
        assertArrayEquals(cover, result?.data)
        assertEquals(
            "thumbnail_1741a2f3-0bcf-4c2c-a6db-b90ab9623427_EBOK_portrait.jpg",
            result?.thumbnailFilename
        )
        assertEquals(
            "thumbnail_B012345678_PDOC_portrait.jpg",
            MobiCoverExtractor.cover(fixture(contentId = "B012345678", contentType = "PDOC"))?.thumbnailFilename
        )
    }

    @Test fun missingOrUnsafeIdentityDoesNotPreventLocalCoverExtraction() {
        val unsafe = listOf("", "../escape", "book/name", "a'\$(command)", "中文", "book\nname", "a".repeat(129))
        for (id in unsafe) {
            val result = MobiCoverExtractor.cover(fixture(contentId = id, contentType = "EBOK"))
            assertArrayEquals("id=$id", cover, result?.data)
            assertNull("id=$id", result?.thumbnailFilename)
        }
        assertNull(MobiCoverExtractor.cover(fixture())?.thumbnailFilename)
        assertNull(
            MobiCoverExtractor.cover(fixture(contentId = "abc", contentType = "OTHER"))?.thumbnailFilename
        )
    }

    @Test fun thumbnailWriteTargetsAreStrictBasenames() {
        assertTrue(MobiCoverExtractor.isSafeThumbnailFilename("thumbnail_book-123_EBOK_portrait.jpg"))
        assertTrue(MobiCoverExtractor.isSafeThumbnailFilename("thumbnail_book_123_PDOC_portrait.jpg"))
        for (name in listOf(
            "thumbnail__EBOK_portrait.jpg",
            "../thumbnail_book_EBOK_portrait.jpg",
            "thumbnail_../book_EBOK_portrait.jpg",
            "thumbnail_book_OTHER_portrait.jpg",
            "thumbnail_book_EBOK_portrait.jpg/extra",
            "thumbnail_book_EBOK_portrait.png"
        )) assertFalse(name, MobiCoverExtractor.isSafeThumbnailFilename(name))
    }

    private fun fixture(
        contentId: String? = null,
        contentType: String? = null,
        coverBytes: ByteArray = cover
    ): ByteArray {
        val metadataBytes = ByteArrayOutputStream().apply {
            for ((type, value) in listOf(113L to contentId, 501L to contentType)) {
                if (value == null) continue
                val bytes = value.toByteArray(Charsets.UTF_8)
                val record = ByteArray(8)
                put(type, record, 0)
                put((8 + bytes.size).toLong(), record, 4)
                write(record)
                write(bytes)
            }
        }.toByteArray()
        val imageStart = exthStart + 36 + metadataBytes.size
        val data = ByteArray(imageStart)
        data[77] = 3
        put(recordZero.toLong(), data, 78)
        put(imageStart.toLong(), data, 86)
        put((imageStart + coverBytes.size).toLong(), data, 94)
        "MOBI".toByteArray(Charsets.US_ASCII).copyInto(data, mobiStart)
        put(232, data, mobiStart + 4)
        put(1, data, mobiStart + 92)
        "EXTH".toByteArray(Charsets.US_ASCII).copyInto(data, exthStart)
        put((36 + metadataBytes.size).toLong(), data, exthStart + 4)
        put((2 + (if (contentId != null) 1 else 0) + (if (contentType != null) 1 else 0)).toLong(), data, exthStart + 8)
        put(201, data, exthStart + 12)
        put(12, data, exthStart + 16)
        put(0, data, exthStart + 20)
        put(202, data, exthStart + 24)
        put(12, data, exthStart + 28)
        put(1, data, exthStart + 32)
        metadataBytes.copyInto(data, exthStart + 36)
        return data + coverBytes + thumbnail
    }

    private fun put(value: Long, data: ByteArray, at: Int) {
        for (index in 0..3) data[at + index] = (value shr ((3 - index) * 8)).toByte()
    }
}
