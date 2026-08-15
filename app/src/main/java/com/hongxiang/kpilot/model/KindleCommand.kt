package com.hongxiang.kpilot.model

enum class KindleCommand(val label: String) {
    PREVIOUS("Previous"),
    NEXT("Next"),
    TRANSLATE("Translate")
}

/** Shared input boundary for the screen, volume keys, and a future Band companion. */
fun interface CommandSink {
    fun submit(command: KindleCommand)
}
