package com.hongxiang.kpilot.data

import java.security.MessageDigest
import java.util.Locale

/** Stable device namespace; credentials and touch-device settings are deliberately excluded. */
fun ConnectionSettings.cacheScope(): String = MessageDigest.getInstance("SHA-256")
    .digest(listOf(host.trim().lowercase(Locale.ROOT), port.toString(), username).joinToString("\u001f").toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
