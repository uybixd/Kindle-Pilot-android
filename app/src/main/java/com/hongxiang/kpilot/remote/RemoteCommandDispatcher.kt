package com.hongxiang.kpilot.remote

import android.content.Context
import com.hongxiang.kpilot.data.AppPreferences
import com.hongxiang.kpilot.data.ConnectionSettings
import com.hongxiang.kpilot.data.SecureSettingsStore
import com.hongxiang.kpilot.model.CommandSink
import com.hongxiang.kpilot.model.KindleCommand
import com.hongxiang.kpilot.ssh.KindleSshClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

data class RemoteDispatchState(
    val busy: Boolean = false,
    val message: String = "翻页遥控未开启",
    val error: Boolean = false,
    val hasCheckedFlipCommands: Boolean = false,
    val missingFlipCommands: List<FlipCommandDefinition> = emptyList(),
    val recordingCommandId: String? = null
)

class RemoteCommandDispatcher private constructor(context: Context) : CommandSink {
    private sealed interface Work {
        data class TurnPage(val command: KindleCommand, val executionLease: Closeable) : Work
        data object CheckCommands : Work
        data class RecordCommand(val definition: FlipCommandDefinition) : Work
    }

    private val preferences = AppPreferences(context)
    private val settingsStore = SecureSettingsStore(context)
    private val ssh = KindleSshClient()
    private val executionGuard = RemoteExecutionGuard(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<Work>(Channel.BUFFERED)
    private val mutableState = MutableStateFlow(RemoteDispatchState())
    @Volatile private var checkedSettings: ConnectionSettings? = null
    val state = mutableState.asStateFlow()

    init {
        scope.launch {
            for (work in queue) {
                when (work) {
                    is Work.TurnPage -> try {
                        processPageTurn(work.command)
                    } finally {
                        work.executionLease.close()
                    }
                    Work.CheckCommands -> processCommandCheck()
                    is Work.RecordCommand -> processRecording(work.definition)
                }
            }
        }
    }

    override fun submit(command: KindleCommand) {
        if (!RemoteCommandGate.allows(preferences.remoteControlEnabled)) {
            mutableState.update { it.copy(message = "请先开启翻页遥控", error = true) }
            return
        }
        val executionLease = executionGuard.acquire()
        if (queue.trySend(Work.TurnPage(command, executionLease)).isSuccess) {
            mutableState.update { it.copy(message = "${command.pageLabel()}已加入队列", error = false) }
        } else {
            executionLease.close()
        }
    }

    fun checkFlipCommands() {
        if (queue.trySend(Work.CheckCommands).isSuccess) {
            mutableState.update { it.copy(message = "准备检查翻页命令", error = false) }
        }
    }

    fun recordFlipCommand(definition: FlipCommandDefinition) {
        if (queue.trySend(Work.RecordCommand(definition)).isSuccess) {
            mutableState.update { it.copy(message = "准备录制${definition.title}", error = false) }
        }
    }

    fun connectionSettingsChanged() {
        checkedSettings = null
        mutableState.update {
            it.copy(
                hasCheckedFlipCommands = false,
                missingFlipCommands = emptyList(),
                message = "连接设置已更新，首次翻页时会检查命令",
                error = false
            )
        }
    }

    fun remoteSettingChanged(enabled: Boolean) {
        mutableState.update {
            it.copy(message = if (enabled) "翻页遥控已开启" else "翻页遥控未开启", error = false)
        }
    }

    private fun processPageTurn(command: KindleCommand) {
        if (!RemoteCommandGate.allows(preferences.remoteControlEnabled)) {
            mutableState.update { it.copy(message = "翻页遥控未开启", error = true) }
            return
        }
        runCatching {
            val settings = loadSettings()
            if (requiresFlipCommandCheck(checkedSettings, settings, mutableState.value.hasCheckedFlipCommands)) {
                mutableState.update { it.copy(busy = true, message = "首次翻页前正在检查命令…", error = false) }
                val missing = ssh.missingFlipCommands(settings)
                updateReadiness(settings, missing)
                if (missing.isNotEmpty()) {
                    mutableState.update {
                        it.copy(
                            busy = false,
                            message = "缺少 ${missing.size} 个翻页命令，请先录制",
                            error = true
                        )
                    }
                    return
                }
            } else if (mutableState.value.missingFlipCommands.isNotEmpty()) {
                mutableState.update {
                    it.copy(busy = false, message = "翻页命令尚未录制完整", error = true)
                }
                return
            }

            mutableState.update { it.copy(busy = true, message = "正在发送${command.pageLabel()}…", error = false) }
            ssh.send(settings, command)
            mutableState.update { it.copy(busy = false, message = "${command.pageLabel()}完成", error = false) }
        }.onFailure(::showFailure)
    }

    private fun processCommandCheck() {
        runCatching {
            val settings = loadSettings()
            mutableState.update { it.copy(busy = true, message = "正在检查 4 个翻页命令…", error = false) }
            val missing = ssh.missingFlipCommands(settings)
            updateReadiness(settings, missing)
            mutableState.update {
                it.copy(
                    busy = false,
                    message = if (missing.isEmpty()) "4 个翻页命令都已就绪" else "缺少 ${missing.size} 个翻页命令",
                    error = false
                )
            }
        }.onFailure(::showFailure)
    }

    private fun processRecording(definition: FlipCommandDefinition) {
        runCatching {
            val settings = loadSettings()
            mutableState.update {
                it.copy(
                    busy = true,
                    recordingCommandId = definition.id,
                    message = "正在录制${definition.title}…",
                    error = false
                )
            }
            ssh.recordFlipCommand(settings, definition)
            val missing = ssh.missingFlipCommands(settings)
            updateReadiness(settings, missing)
            mutableState.update {
                it.copy(
                    busy = false,
                    recordingCommandId = null,
                    message = if (missing.isEmpty()) {
                        "${definition.title}录制完成，4 个命令已就绪"
                    } else {
                        "${definition.title}录制完成，还缺少 ${missing.size} 个命令"
                    },
                    error = false
                )
            }
        }.onFailure(::showFailure)
    }

    private fun updateReadiness(settings: ConnectionSettings, missing: List<FlipCommandDefinition>) {
        checkedSettings = settings
        mutableState.update {
            it.copy(hasCheckedFlipCommands = true, missingFlipCommands = missing)
        }
    }

    private fun loadSettings(): ConnectionSettings =
        settingsStore.load() ?: error("请先保存 Kindle 连接设置")

    private fun showFailure(error: Throwable) {
        mutableState.update {
            it.copy(
                busy = false,
                recordingCommandId = null,
                message = error.message ?: "翻页操作失败",
                error = true
            )
        }
    }

    private fun KindleCommand.pageLabel() = when (this) {
        KindleCommand.PREVIOUS -> "上一页"
        KindleCommand.NEXT -> "下一页"
        KindleCommand.TRANSLATE -> "翻译"
    }

    companion object {
        @Volatile private var instance: RemoteCommandDispatcher? = null

        fun get(context: Context): RemoteCommandDispatcher = instance ?: synchronized(this) {
            instance ?: RemoteCommandDispatcher(context.applicationContext).also { instance = it }
        }
    }
}
