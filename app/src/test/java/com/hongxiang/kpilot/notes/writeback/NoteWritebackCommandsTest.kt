package com.hongxiang.kpilot.notes.writeback

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit

class NoteWritebackCommandsTest {
    @get:Rule val temporary = TemporaryFolder()

    @Before fun requirePosixShell() {
        assumeTrue(File("/bin/sh").canExecute())
        assumeTrue(Files.getFileStore(temporary.root.toPath()).supportsFileAttributeView("posix"))
    }

    @Test fun commitAndRecoveryQuotePathsAndPreserveNativePermissions() {
        val folder = temporary.newFolder("Book's \$(literal) 中文")
        val live = File(folder, "Book's annotations.azw3r").apply { writeText("original") }
        Files.setPosixFilePermissions(live.toPath(), PosixFilePermissions.fromString("rw-r-----"))
        val expected = File(folder, "expected").apply { writeText("original") }
        val staged = File(folder, "staged")
        val backup = File(folder, "backup")
        val partial = File(folder, "backup.partial")
        val quote = SshNoteWritebackConnection::quote
        assertEquals(0, shell("set -e; cp -p ${quote(live.path)} ${quote(staged.path)}; cat > ${quote(staged.path)}", "updated"))
        assertEquals(0, shell(NoteWritebackCommands.commit(live.path, expected.path, staged.path, backup.path, partial.path)))
        assertEquals("updated", live.readText())
        assertEquals("original", backup.readText())
        assertEquals(PosixFilePermissions.fromString("rw-r-----"), Files.getPosixFilePermissions(live.toPath()))
        val written = File(folder, "written").apply { writeText("updated") }
        assertEquals(0, shell(NoteWritebackCommands.restore(live.path, backup.path, written.path, File(folder, "restore").path)))
        assertEquals("original", live.readText())
        assertEquals(PosixFilePermissions.fromString("rw-r-----"), Files.getPosixFilePermissions(live.toPath()))
    }

    @Test fun concurrentChangesAreNotOverwrittenByCommitOrRollback() {
        val folder = temporary.newFolder()
        val live = File(folder, "live").apply { writeText("another writer") }
        val expected = File(folder, "expected").apply { writeText("original") }
        val staged = File(folder, "staged").apply { writeText("our update") }
        val backup = File(folder, "backup")
        assertEquals(74, shell(NoteWritebackCommands.commit(live.path, expected.path, staged.path, backup.path, "$backup.partial")))
        assertFalse(backup.exists())
        assertEquals("another writer", live.readText())
        backup.writeText("original")
        expected.writeText("our update")
        assertEquals(75, shell(NoteWritebackCommands.restore(live.path, backup.path, expected.path, File(folder, "restore").path)))
        assertEquals("another writer", live.readText())
        assertEquals("original", backup.readText())
    }

    @Test fun recoveryIsIdempotentAndMissingBackupLeavesOriginalAlone() {
        val folder = temporary.newFolder()
        val live = File(folder, "live").apply { writeText("original") }
        val expected = File(folder, "expected").apply { writeText("our update") }
        val backup = File(folder, "backup")
        val command = NoteWritebackCommands.restore(live.path, backup.path, expected.path, File(folder, "restore").path)
        assertEquals(0, shell(command))
        backup.writeText("original")
        assertEquals(0, shell(command))
        assertEquals("original", live.readText())
    }

    private fun shell(command: String, input: String = ""): Int {
        val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
        process.outputStream.use { it.write(input.toByteArray()) }
        assertTrue("Shell command timed out", process.waitFor(15, TimeUnit.SECONDS))
        process.inputStream.close()
        return process.exitValue()
    }
}
