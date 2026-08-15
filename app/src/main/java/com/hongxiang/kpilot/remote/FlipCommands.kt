package com.hongxiang.kpilot.remote

import com.hongxiang.kpilot.data.ConnectionSettings
import com.hongxiang.kpilot.model.KindleCommand

internal fun requiresFlipCommandCheck(
    checkedSettings: ConnectionSettings?,
    currentSettings: ConnectionSettings,
    hasChecked: Boolean
): Boolean = !hasChecked || checkedSettings != currentSettings

enum class KindleScreenOrientation(val remoteName: String, val label: String) {
    PORTRAIT("portrait", "竖屏"),
    LANDSCAPE("landscape", "横屏")
}

data class FlipCommandDefinition(
    val command: KindleCommand,
    val orientation: KindleScreenOrientation
) {
    init {
        require(command == KindleCommand.NEXT || command == KindleCommand.PREVIOUS)
    }

    val id: String
        get() = "${command.remoteEventName}_${orientation.remoteName}"

    val title: String
        get() = "${orientation.label}${if (command == KindleCommand.NEXT) "下一页" else "上一页"}"

    val remotePath: String
        get() = "/mnt/us/FlipCmd/$id.event"

    companion object {
        val all = listOf(
            FlipCommandDefinition(KindleCommand.NEXT, KindleScreenOrientation.PORTRAIT),
            FlipCommandDefinition(KindleCommand.PREVIOUS, KindleScreenOrientation.PORTRAIT),
            FlipCommandDefinition(KindleCommand.NEXT, KindleScreenOrientation.LANDSCAPE),
            FlipCommandDefinition(KindleCommand.PREVIOUS, KindleScreenOrientation.LANDSCAPE)
        )

        internal fun missingFromProbeOutput(output: String): List<FlipCommandDefinition> {
            val missingPaths = output.lineSequence().map(String::trim).filter(String::isNotEmpty).toSet()
            return all.filter { it.remotePath in missingPaths }
        }
    }
}

internal val KindleCommand.remoteEventName: String
    get() = when (this) {
        KindleCommand.NEXT -> "next"
        KindleCommand.PREVIOUS -> "prev"
        KindleCommand.TRANSLATE -> error("Translate has no page-turn event")
    }
