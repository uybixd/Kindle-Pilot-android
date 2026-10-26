package com.hongxiang.kpilot.ssh

import android.os.Build
import com.hongxiang.kpilot.data.ConnectionSettings
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session

/** Creates consistently configured SSH sessions for every Kindle feature. */
object KindleSshSessionFactory {
    @Volatile var knownHostsFile: java.io.File? = null
    fun create(settings: ConnectionSettings): Session =
        JSch().apply { knownHostsFile?.let { file ->
            file.parentFile?.mkdirs(); if (!file.exists()) file.createNewFile(); setKnownHosts(file.absolutePath)
        } }.getSession(settings.username, settings.host, settings.port).also { session ->
            session.setPassword(settings.password)
            configure(session, Build.VERSION.SDK_INT)
        }

    internal fun configure(session: Session, sdkInt: Int) {
        // This intentionally mirrors Paramiko AutoAddPolicy used by the desktop client.
        session.setConfig("StrictHostKeyChecking", "no")
        // KPilot only supplies password credentials; avoid probing unavailable key implementations.
        session.setConfig("PreferredAuthentications", "password,keyboard-interactive")

        if (sdkInt < EC_ALGORITHM_PARAMETERS_API) {
            // Android added AlgorithmParameters.EC in API 26. JSch's ECDH/ECDSA implementations
            // can otherwise fail before authentication on Android 7.x, even though finite-field DH
            // and RSA are available. Keep stronger non-EC options first and the old-Kindle
            // SHA-1 algorithms as last-resort interoperability fallbacks.
            session.setConfig("kex", LEGACY_ANDROID_KEX)
            session.setConfig("server_host_key", LEGACY_ANDROID_HOST_KEYS)
        }
    }

    internal const val EC_ALGORITHM_PARAMETERS_API = 26
    internal const val LEGACY_ANDROID_KEX =
        "diffie-hellman-group14-sha256," +
            "diffie-hellman-group-exchange-sha256," +
            "diffie-hellman-group16-sha512," +
            "diffie-hellman-group14-sha1," +
            "diffie-hellman-group-exchange-sha1," +
            "diffie-hellman-group1-sha1"
    internal const val LEGACY_ANDROID_HOST_KEYS = "rsa-sha2-512,rsa-sha2-256,ssh-rsa"
}
