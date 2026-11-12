package com.hongxiang.kpilot.data

import java.security.MessageDigest
import java.util.Locale

/**
 * Stable data namespace. The Kindle identity written under /mnt/us/.kpilot/device-id
 * survives IP changes and is shared by every phone connected to that Kindle.
 * The hash keeps the 64-hex shape used by every existing cache file name.
 */
fun ConnectionSettings.cacheScope(): String {
    val stableId = kindleId.trim()
    if (stableId.isNotEmpty()) return sha256("kindle\u001f$stableId")
    return legacyCacheScope()
}

/** Pre-identity namespace. Kept only to migrate data on the first successful lookup. */
fun ConnectionSettings.legacyCacheScope(): String = sha256(
    listOf(host.trim().lowercase(Locale.ROOT), port.toString(), username)
        .joinToString("\u001f")
)

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
