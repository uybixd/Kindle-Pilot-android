package com.hongxiang.kpilot.band

import android.content.Context
import com.hongxiang.kpilot.data.AppPreferences
import com.hongxiang.kpilot.remote.RemoteCommandDispatcher
import com.xiaomi.xms.wearable.Wearable
import com.xiaomi.xms.wearable.auth.Permission
import com.xiaomi.xms.wearable.message.OnMessageReceivedListener
import com.xiaomi.xms.wearable.service.OnServiceConnectionListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class XiaomiBandState(
    val enabled: Boolean = false,
    val connected: Boolean = false,
    val message: String = "Xiaomi Band remote is off",
    val error: Boolean = false
)

class XiaomiBandController private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = AppPreferences(appContext)
    private val dispatcher = RemoteCommandDispatcher.get(appContext)
    private val nodeApi by lazy { Wearable.getNodeApi(appContext) }
    private val authApi by lazy { Wearable.getAuthApi(appContext) }
    private val messageApi by lazy { Wearable.getMessageApi(appContext) }
    private val serviceApi by lazy { Wearable.getServiceApi(appContext) }
    private val recentIds = RecentBandCommandIds()
    private val mutableState = MutableStateFlow(
        XiaomiBandState(enabled = preferences.xiaomiBandEnabled)
    )
    val state = mutableState.asStateFlow()

    @Volatile private var currentNodeId: String? = null
    @Volatile private var listenerRegistered = false
    @Volatile private var listenerRegistrationInFlight = false
    @Volatile private var serviceListenerRegistered = false

    private val messageListener = OnMessageReceivedListener { nodeId, payload ->
        handleMessage(nodeId, payload)
    }

    private val serviceListener = object : OnServiceConnectionListener {
        override fun onServiceConnected() {
            if (preferences.xiaomiBandEnabled) connect(requestPermission = false)
        }

        override fun onServiceDisconnected() {
            listenerRegistered = false
            listenerRegistrationInFlight = false
            currentNodeId = null
            mutableState.value = XiaomiBandState(
                enabled = preferences.xiaomiBandEnabled,
                message = "Xiaomi wearable service disconnected",
                error = true
            )
        }
    }

    fun start(requestPermission: Boolean) {
        if (!preferences.xiaomiBandEnabled) {
            mutableState.value = XiaomiBandState(message = "Xiaomi Band remote is off")
            return
        }
        runCatching {
            ensureServiceListener()
            connect(requestPermission)
        }.onFailure { failure -> reportFailure("Could not start Xiaomi wearable support", failure) }
    }

    fun setEnabled(enabled: Boolean) {
        preferences.xiaomiBandEnabled = enabled
        if (enabled) {
            mutableState.value = XiaomiBandState(enabled = true, message = "Looking for a paired Xiaomi wearable…")
            start(requestPermission = true)
        } else {
            unregisterMessageListener()
            mutableState.value = XiaomiBandState(message = "Xiaomi Band remote is off")
        }
    }

    fun refresh() {
        if (preferences.xiaomiBandEnabled) connect(requestPermission = false)
    }

    /** Manual retry from the UI: allow re-prompting for wearable permission. */
    fun reconnect() {
        if (preferences.xiaomiBandEnabled) connect(requestPermission = true)
    }

    private fun ensureServiceListener() {
        if (serviceListenerRegistered) return
        serviceApi.registerServiceConnectionListener(serviceListener)
        serviceListenerRegistered = true
    }

    private fun connect(requestPermission: Boolean) {
        mutableState.value = XiaomiBandState(enabled = true, message = "Looking for a paired Xiaomi wearable…")
        nodeApi.connectedNodes
            .addOnSuccessListener { nodes ->
                val node = nodes.firstOrNull()
                if (node == null) {
                    currentNodeId = null
                    listenerRegistered = false
                    mutableState.value = XiaomiBandState(
                        enabled = true,
                        message = "No paired Xiaomi wearable is connected",
                        error = true
                    )
                    return@addOnSuccessListener
                }
                currentNodeId = node.id
                authApi.checkPermission(node.id, Permission.DEVICE_MANAGER)
                    .addOnSuccessListener { granted ->
                        when {
                            granted -> registerMessageListener(node.id)
                            requestPermission -> requestDevicePermission(node.id)
                            else -> mutableState.value = XiaomiBandState(
                                enabled = true,
                                message = "Wearable permission required; turn the switch off and on to approve",
                                error = true
                            )
                        }
                    }
                    .addOnFailureListener { failure ->
                        // checkPermission fails outright when the stored grant is bound
                        // to a different signing certificate than the running APK
                        // (e.g. after switching between debug and release builds).
                        // Re-requesting re-binds the grant to the current signature.
                        if (requestPermission) requestDevicePermission(node.id)
                        else reportFailure("Could not check wearable permission", failure)
                    }
            }
            .addOnFailureListener { failure -> reportFailure("Could not query paired Xiaomi wearables", failure) }
    }

    private fun requestDevicePermission(nodeId: String) {
        mutableState.value = XiaomiBandState(enabled = true, message = "Approve wearable access on the phone…")
        authApi.requestPermission(nodeId, Permission.DEVICE_MANAGER)
            .addOnSuccessListener { granted ->
                if (granted.any { it == Permission.DEVICE_MANAGER }) {
                    registerMessageListener(nodeId)
                } else {
                    mutableState.value = XiaomiBandState(
                        enabled = true,
                        message = "Xiaomi wearable permission was not granted",
                        error = true
                    )
                }
            }
            .addOnFailureListener { failure -> reportFailure("Wearable permission request failed", failure) }
    }

    private fun registerMessageListener(nodeId: String) {
        if (listenerRegistered && currentNodeId == nodeId) {
            mutableState.value = XiaomiBandState(enabled = true, connected = true, message = "Xiaomi Band remote is ready")
            return
        }
        if (listenerRegistrationInFlight) return
        listenerRegistrationInFlight = true
        messageApi.addListener(nodeId, messageListener)
            .addOnSuccessListener {
                markMessageListenerReady(nodeId)
            }
            .addOnFailureListener { failure ->
                listenerRegistrationInFlight = false
                // The wearable service reports this when two startup paths race
                // to register the same package. The earlier registration remains
                // valid, so do not overwrite the ready state with a false error.
                if (failure.message?.contains("registered", ignoreCase = true) == true) {
                    markMessageListenerReady(nodeId)
                } else {
                    reportFailure("Could not listen for Band commands", failure)
                }
            }
    }

    private fun markMessageListenerReady(nodeId: String) {
        listenerRegistrationInFlight = false
        currentNodeId = nodeId
        listenerRegistered = true
        mutableState.value = XiaomiBandState(
            enabled = true,
            connected = true,
            message = "Xiaomi Band remote is ready"
        )
    }

    private fun unregisterMessageListener() {
        val nodeId = currentNodeId ?: return
        messageApi.removeListener(nodeId)
            .addOnSuccessListener {
                listenerRegistrationInFlight = false
                listenerRegistered = false
                currentNodeId = null
            }
            .addOnFailureListener { failure -> reportFailure("Could not stop the Band listener", failure) }
    }

    private fun handleMessage(nodeId: String, payload: ByteArray) {
        val pageCommand = BandCommandProtocol.decode(payload) ?: return
        val status = when {
            nodeId != currentNodeId -> "invalid"
            recentIds.isDuplicate(pageCommand.requestId) -> "duplicate"
            !preferences.remoteControlEnabled -> "remote_off"
            else -> {
                dispatcher.submit(pageCommand.command)
                "accepted"
            }
        }
        messageApi.sendMessage(nodeId, BandCommandProtocol.acknowledgement(pageCommand.requestId, status))
        mutableState.value = XiaomiBandState(
            enabled = true,
            connected = true,
            message = when (status) {
                "accepted" -> "Band ${pageCommand.command.label.lowercase()} command queued"
                "duplicate" -> "Duplicate Band command ignored"
                "remote_off" -> "Band connected; Remote page control is off"
                else -> "Band command rejected"
            },
            error = status == "remote_off" || status == "invalid"
        )
    }

    private fun reportFailure(prefix: String, failure: Throwable) {
        listenerRegistrationInFlight = false
        listenerRegistered = false
        mutableState.value = XiaomiBandState(
            enabled = preferences.xiaomiBandEnabled,
            message = "$prefix: ${failure.message ?: failure.javaClass.simpleName}",
            error = true
        )
    }

    companion object {
        @Volatile private var instance: XiaomiBandController? = null

        fun get(context: Context): XiaomiBandController = instance ?: synchronized(this) {
            instance ?: XiaomiBandController(context).also { instance = it }
        }
    }
}
