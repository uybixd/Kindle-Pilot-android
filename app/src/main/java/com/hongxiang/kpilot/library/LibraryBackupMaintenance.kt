package com.hongxiang.kpilot.library

/** Old iOS note writes used hidden TXT backups, which Android used to mistake for books. */
internal object LibraryBackupMaintenance {
    private const val DOCUMENTS = "/mnt/us/documents/"
    private const val ARCHIVE_ROOT = "/mnt/us/.kpilot/backups/clippings"
    private val legacyName = Regex(
        "\\.My Clippings\\.kpilot-backup-(?:[0-9]+-[0-9a-f]{8}|" +
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\\.txt",
        RegexOption.IGNORE_CASE
    )
    private val timestampName = Regex(
        "\\.My Clippings\\.kpilot-backup-([0-9]+)-[0-9a-f]{8}\\.txt",
        RegexOption.IGNORE_CASE
    )

    val listCommand = listCommand(DOCUMENTS.removeSuffix("/"))

    internal fun listCommand(directory: String): String = "test -d ${quote(directory)} && find ${quote(directory)} -maxdepth 1 " +
        "-type f -name '.My Clippings.kpilot-backup-*.txt' -mtime +0 -print0"

    fun isLegacyClippingsBackup(path: String): Boolean = path.startsWith(DOCUMENTS) &&
        legacyName.matches(path.removePrefix(DOCUMENTS))

    fun archiveCommand(path: String): String {
        require(isLegacyClippingsBackup(path))
        // VFAT can report the copied mtime as ctime, so a filesystem timestamp alone
        // cannot establish a generation's age. UUID-only generations stay in place.
        val generatedAt = timestampName.matchEntire(path.substringAfterLast('/'))
            ?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it > 0 } ?: return ":"
        val name = path.substringAfterLast('/').removePrefix(".").dropLast(4)
        return ageCheckedMoveCommand(path, "$ARCHIVE_ROOT/$name", "$name.bak", generatedAt)
    }

    // cp -p preserves the original TXT's mtime even for new rollback copies. Older
    // Kindle find builds lack -ctime, so check stat's ctime immediately before moving.
    // Missing tools, unsupported flags, invalid timestamps and recent copies all skip
    // maintenance safely while the ordinary book scan can still proceed.
    internal fun ageCheckedMoveCommand(
        source: String,
        destinationDirectory: String,
        name: String,
        generatedAtEpochSeconds: Long,
        clockCommand: String = "date +%s",
        changedAtCommand: String = "stat -c %Z ${quote(source)}"
    ): String {
        if (generatedAtEpochSeconds <= 0) return ":"
        val now = "\"\${kpilot_backup_now}\""
        val changed = "\"\${kpilot_backup_changed}\""
        return "kpilot_backup_now=\$($clockCommand 2>/dev/null) || exit 0; " +
            "kpilot_backup_changed=\$($changedAtCommand 2>/dev/null) || exit 0; " +
            "case $now in ''|*[!0-9]*) exit 0;; esac; " +
            "case $changed in ''|*[!0-9]*) exit 0;; esac; " +
            "if [ $now -gt $generatedAtEpochSeconds ] && " +
            "[ \"\$((kpilot_backup_now - $generatedAtEpochSeconds))\" -gt 86400 ] && " +
            "[ $now -gt $changed ] && [ \"\$((kpilot_backup_now - kpilot_backup_changed))\" -gt 86400 ]; then " +
            moveCommand(source, destinationDirectory, name) + "; fi"
    }

    /** mkdir claims a destination atomically; existing archives are never overwritten. */
    internal fun moveCommand(source: String, destinationDirectory: String, name: String): String {
        val parent = destinationDirectory.substringBeforeLast('/')
        val destination = "$destinationDirectory/$name"
        return "set -e; if [ -f ${quote(source)} ] && [ ! -L ${quote(source)} ]; then " +
            "mkdir -p ${quote(parent)}; " +
            "if mkdir ${quote(destinationDirectory)} 2>/dev/null; then " +
            "if ! mv ${quote(source)} ${quote(destination)}; then " +
            "rmdir ${quote(destinationDirectory)} 2>/dev/null || true; exit 1; fi; fi; fi"
    }

    private fun quote(value: String) = "'${value.replace("'", "'\\''")}'"
}
