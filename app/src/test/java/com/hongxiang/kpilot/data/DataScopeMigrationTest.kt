package com.hongxiang.kpilot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DataScopeMigrationTest {
    private val from = "a".repeat(64)
    private val to = "b".repeat(64)

    @Test fun movesCachesAndRewritesSnapshotSourceKey() {
        val root = Files.createTempDirectory("scope-migration").toFile()
        try {
            root.resolve("library/$from/covers").mkdirs()
            root.resolve("library/$from/covers/cover.jpg").writeText("image")
            root.resolve("library/$from/snapshot.json")
                .writeText("{\"sourceKey\":\"$from\",\"books\":[]}")
            root.resolve("notes/$from").mkdirs()
            root.resolve("notes/$from/My Clippings.txt").writeText("clippings")
            root.resolve("vocabulary/$from").mkdirs()
            root.resolve("vocabulary/$from/vocab.db").writeText("vocab")
            root.resolve("note-drafts").mkdirs()
            root.resolve("note-drafts/$from.dat").writeText("drafts")
            root.resolve("learning").mkdirs()
            root.resolve("learning/$from.json").writeText("learning")

            DataScopeMigration.migrate(root, from, to)

            assertEquals("image", root.resolve("library/$to/covers/cover.jpg").readText())
            assertEquals(
                "{\"sourceKey\":\"$to\",\"books\":[]}",
                root.resolve("library/$to/snapshot.json").readText()
            )
            assertEquals("clippings", root.resolve("notes/$to/My Clippings.txt").readText())
            assertEquals("vocab", root.resolve("vocabulary/$to/vocab.db").readText())
            assertEquals("drafts", root.resolve("note-drafts/$to.dat").readText())
            assertEquals("learning", root.resolve("learning/$to.json").readText())
            assertFalse(root.resolve("library/$from").exists())
            assertFalse(root.resolve("notes/$from").exists())
            assertFalse(root.resolve("vocabulary/$from").exists())
            assertFalse(root.resolve("note-drafts/$from.dat").exists())
            assertFalse(root.resolve("learning/$from.json").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun anExistingTargetIsNeverOverwritten() {
        val root = Files.createTempDirectory("scope-existing").toFile()
        try {
            root.resolve("library/$from").mkdirs()
            root.resolve("library/$from/snapshot.json").writeText("old")
            root.resolve("library/$to").mkdirs()
            root.resolve("library/$to/snapshot.json").writeText("new")

            DataScopeMigration.migrate(root, from, to)

            assertEquals("new", root.resolve("library/$to/snapshot.json").readText())
            assertTrue(root.resolve("library/$from/snapshot.json").isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun sameOrMalformedScopesAreRejectedOrIgnored() {
        val root = Files.createTempDirectory("scope-invalid").toFile()
        try {
            DataScopeMigration.migrate(root, from, from)
            assertThrows(IllegalArgumentException::class.java) {
                DataScopeMigration.migrate(root, "short", to)
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
