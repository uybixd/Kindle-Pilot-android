package com.hongxiang.kpilot.library

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class LibraryBackupMaintenanceTest {
    @get:Rule val temporary = TemporaryFolder()

    @Before fun requirePosixShell() {
        assumeTrue(File("/bin/sh").canExecute())
    }

    @Test fun onlyKnownNamesAreRecognizedAndOnlyTimestampedGenerationsAreAutomaticallyArchived() {
        assertTrue(LibraryBackupMaintenance.isLegacyClippingsBackup("/mnt/us/documents/.My Clippings.kpilot-backup-1780226200-a0b1c2d3.txt"))
        assertTrue(LibraryBackupMaintenance.isLegacyClippingsBackup("/mnt/us/documents/.My Clippings.kpilot-backup-01234567-89AB-CDEF-0123-456789ABCDEF.txt"))
        listOf(
            "/mnt/us/documents/My Clippings.txt",
            "/mnt/us/documents/.My Clippings.kpilot-backup-personal.txt",
            "/mnt/us/documents/.My Clippings.kpilot-backup-1780226200-a0b1c2d3.pdf",
            "/mnt/us/documents/Subfolder/.My Clippings.kpilot-backup-1780226200-a0b1c2d3.txt",
            "/mnt/us/documents/../.My Clippings.kpilot-backup-1780226200-a0b1c2d3.txt",
            "/mnt/us/system/.My Clippings.kpilot-backup-1780226200-a0b1c2d3.txt"
        ).forEach { path ->
            assertFalse(path, LibraryBackupMaintenance.isLegacyClippingsBackup(path))
            assertThrows(IllegalArgumentException::class.java) { LibraryBackupMaintenance.archiveCommand(path) }
        }
        assertTrue(LibraryBackupMaintenance.archiveCommand(
            "/mnt/us/documents/.My Clippings.kpilot-backup-1780226200-a0b1c2d3.txt"
        ).contains("/mnt/us/.kpilot/backups/clippings/"))
        assertEquals(":", LibraryBackupMaintenance.archiveCommand(
            "/mnt/us/documents/.My Clippings.kpilot-backup-01234567-89AB-CDEF-0123-456789ABCDEF.txt"
        ))
        assertEquals(":", LibraryBackupMaintenance.archiveCommand(
            "/mnt/us/documents/.My Clippings.kpilot-backup-99999999999999999999999999-a0b1c2d3.txt"
        ))
    }

    @Test fun aNewBackupWithPreservedOldModificationTimeIsLeftForAnotherClientsRollback() {
        val documents = temporary.newFolder("documents")
        val generatedAt = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) - TimeUnit.DAYS.toSeconds(3)
        val source = File(documents, ".My Clippings.kpilot-backup-$generatedAt-a0b1c2d3.txt")
            .apply { writeText("rollback copy") }
        // iOS copies with cp -p: the backup's mtime may be old while its ctime is new.
        assertTrue(source.setLastModified(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(3)))
        val process = ProcessBuilder("sh", "-c", LibraryBackupMaintenance.listCommand(documents.path))
            .redirectErrorStream(true).start()
        process.outputStream.close()
        assertTrue(process.waitFor(15, TimeUnit.SECONDS))
        val selected = process.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        assertEquals(selected, 0, process.exitValue())
        assertEquals(source.path + '\u0000', selected)
        val archive = File(temporary.root, "archive/recent")
        assertEquals(0, shell(LibraryBackupMaintenance.ageCheckedMoveCommand(
            source.path, archive.path, "clippings.bak", generatedAt, changedAtCommand = ctimeCommand(source)
        )))
        assertEquals("rollback copy", source.readText())
        assertFalse(archive.exists())
    }

    @Test fun anOldChangeTimeAllowsArchivingWhileMissingOrInvalidTimesSkipSafely() {
        val source = temporary.newFile("backup.txt").apply { writeText("rollback copy") }
        val archive = File(temporary.root, "archive/old")
        val generatedAt = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) - TimeUnit.DAYS.toSeconds(3)
        listOf("false", "printf unknown", "printf ''").forEach { changedAt ->
            assertEquals(0, shell(LibraryBackupMaintenance.ageCheckedMoveCommand(
                source.path, archive.path, "clippings.bak", generatedAt, changedAtCommand = changedAt
            )))
            assertTrue(source.isFile)
            assertFalse(archive.exists())
        }
        assertEquals(0, shell(LibraryBackupMaintenance.ageCheckedMoveCommand(
            source.path, archive.path, "clippings.bak", generatedAt, clockCommand = "false",
            changedAtCommand = ctimeCommand(source)
        )))
        assertTrue(source.isFile)
        val futureNow = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) + TimeUnit.DAYS.toSeconds(2)
        assertEquals(0, shell(LibraryBackupMaintenance.ageCheckedMoveCommand(
            source.path, archive.path, "clippings.bak", generatedAt, clockCommand = "printf $futureNow",
            changedAtCommand = ctimeCommand(source)
        )))
        assertFalse(source.exists())
        assertEquals("rollback copy", File(archive, "clippings.bak").readText())
    }

    @Test fun recentAndFutureFilenameTimestampsPreventMovingEvenWhenVfatReportsOldCtime() {
        val source = temporary.newFile("backup.txt").apply { writeText("rollback copy") }
        val archive = File(temporary.root, "archive/recent")
        val now = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
        val oldTime = now - TimeUnit.DAYS.toSeconds(3)
        listOf(now, now - TimeUnit.HOURS.toSeconds(12), now - TimeUnit.DAYS.toSeconds(1), now + TimeUnit.DAYS.toSeconds(2), 0L).forEach { generatedAt ->
            assertEquals(0, shell(LibraryBackupMaintenance.ageCheckedMoveCommand(
                source.path, archive.path, "clippings.bak", generatedAt,
                clockCommand = "printf $now", changedAtCommand = "printf $oldTime"
            )))
            assertEquals("rollback copy", source.readText())
            assertFalse(archive.exists())
        }
    }

    @Test fun archiveMovesBackupBytesOutsideDocumentsAndLeavesLiveClippingsUntouched() {
        val documents = temporary.newFolder("Kindle's \$(literal) 中文")
        val source = File(documents, ".My Clippings.kpilot-backup-1780226200-a0b1c2d3.txt")
            .apply { writeText("previous clippings") }
        val live = File(documents, "My Clippings.txt").apply { writeText("current clippings") }
        val archive = File(temporary.root, "backups/old generation")
        val command = LibraryBackupMaintenance.moveCommand(source.path, archive.path, "clippings.bak")
        assertEquals(0, shell(command))
        assertFalse(source.exists())
        assertEquals("previous clippings", File(archive, "clippings.bak").readText())
        assertEquals("current clippings", live.readText())
        assertEquals(0, shell(command))
        assertEquals("previous clippings", File(archive, "clippings.bak").readText())
    }

    @Test fun existingArchiveIsNotOverwrittenOrRemoved() {
        val source = temporary.newFile("backup.txt").apply { writeText("new copy") }
        val archive = temporary.newFolder("archive")
        val archived = File(archive, "clippings.bak").apply { writeText("earlier copy") }
        assertEquals(0, shell(LibraryBackupMaintenance.moveCommand(source.path, archive.path, archived.name)))
        assertEquals("new copy", source.readText())
        assertEquals("earlier copy", archived.readText())
    }

    @Test fun concurrentMaintenanceClaimsArchiveOnlyOnce() {
        val source = temporary.newFile("backup.txt").apply { writeText("original backup") }
        val archive = File(temporary.root, "archive/generation")
        val command = LibraryBackupMaintenance.moveCommand(source.path, archive.path, "clippings.bak")
        val processes = (1..2).map { ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start() }
        processes.forEach { process ->
            assertTrue(process.waitFor(15, TimeUnit.SECONDS))
            process.inputStream.close()
            assertEquals(0, process.exitValue())
        }
        assertFalse(source.exists())
        assertEquals("original backup", File(archive, "clippings.bak").readText())
    }

    @Test fun symbolicLinksAreNeverArchived() {
        val live = temporary.newFile("My Clippings.txt").apply { writeText("live") }
        val link = File(temporary.root, "backup.txt")
        Files.createSymbolicLink(link.toPath(), live.toPath())
        val archive = File(temporary.root, "archive/generation")
        assertEquals(0, shell(LibraryBackupMaintenance.moveCommand(link.path, archive.path, "clippings.bak")))
        assertTrue(Files.isSymbolicLink(link.toPath()))
        assertEquals("live", live.readText())
        assertFalse(archive.exists())
    }

    private fun shell(command: String): Int {
        val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
        process.outputStream.close()
        assertTrue("Shell command timed out", process.waitFor(15, TimeUnit.SECONDS))
        process.inputStream.close()
        return process.exitValue()
    }

    private fun ctimeCommand(file: File): String {
        val flags = if (System.getProperty("os.name").orEmpty().startsWith("Mac")) "-f %c" else "-c %Z"
        return "stat $flags '${file.path.replace("'", "'\\''")}'"
    }
}
