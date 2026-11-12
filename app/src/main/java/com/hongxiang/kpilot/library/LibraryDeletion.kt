package com.hongxiang.kpilot.library

/** Builds the same quoted deletion transaction the iOS app sends. */
internal object LibraryDeletion {
    fun command(remotePath: String, includingReadingData: Boolean): String {
        require(LibraryRules.isBookPath(remotePath)) {
            "拒绝操作不安全的 Kindle 路径：$remotePath"
        }
        val deleteBook = "rm -f -- ${quote(remotePath)}"
        if (!includingReadingData) return "set -e; $deleteBook; sync"
        val sidecar = remotePath.substringBeforeLast('.') + ".sdr"
        return "set -e; $deleteBook; rm -rf -- ${quote(sidecar)}; sync"
    }

    private fun quote(value: String) = "'${value.replace("'", "'\\''")}'"
}
