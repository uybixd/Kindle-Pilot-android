package com.hongxiang.kpilot.data

data class ConnectionSettings(
    val host: String = "",
    val port: Int = 22,
    val username: String = "root",
    val password: String = "",
    val eventDevice: String = "",
    /** Stable identity stored on the Kindle; empty until the first successful lookup. */
    val kindleId: String = ""
) {
    fun validate() {
        require(host.isNotBlank()) { "Kindle host is required" }
        require(port in 1..65535) { "Port must be between 1 and 65535" }
        require(username.matches(Regex("[A-Za-z0-9._-]+"))) { "Username contains unsupported characters" }
        require(eventDevice.isBlank() || eventDevice.matches(Regex("event[0-9]+"))) {
            "Touch device must look like event1"
        }
        require(kindleId.isBlank() || kindleId.matches(Regex("[A-Za-z0-9._-]{1,128}"))) {
            "Kindle ID contains unsupported characters"
        }
    }
}
