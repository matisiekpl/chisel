package com.mateuszwozniak.chisel.cli

import com.google.gson.JsonObject
import com.intellij.openapi.Disposable
import com.mateuszwozniak.chisel.model.AgentMode
import com.mateuszwozniak.chisel.model.PromptAttachment
import com.mateuszwozniak.chisel.model.SessionOptions
import com.mateuszwozniak.chisel.protocol.IncomingFrame
import com.mateuszwozniak.chisel.protocol.StreamEvent
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class ClaudeSession(
    private val commandBuilder: ClaudeCommandBuilder,
    private val listener: SessionListener,
) : Disposable {

    private val codec = StreamJsonCodec()

    private var process: ClaudeProcess? = null
    private var control: ControlChannel? = null
    private var handshake: CompletableFuture<JsonObject?> = CompletableFuture.completedFuture(null)

    @Volatile
    var sessionId: String? = null
        private set

    fun start(options: SessionOptions, start: SessionStart) {
        stop()
        sessionId = start.sessionId
        val started = ClaudeProcess(
            commandLine = commandBuilder.build(options, start),
            onLine = ::handleLine,
            onStandardError = listener::onStandardError,
            onTerminated = ::handleTerminated,
        )
        process = started
        control = ControlChannel(codec) { started.send(it) }
        started.start()
        handshake = control!!.request("initialize")
            .completeOnTimeout(null, HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    fun isRunning(): Boolean = process?.isRunning() == true

    fun prompt(text: String, attachments: List<PromptAttachment>) {
        handshake.whenComplete { _, _ -> process?.send(codec.userMessage(text, attachments)) }
    }

    fun interrupt(): CompletableFuture<JsonObject?> = request("interrupt")

    fun changeMode(mode: AgentMode): CompletableFuture<JsonObject?> =
        request("set_permission_mode") { addProperty("mode", mode.permissionMode) }

    fun rewindFiles(messageUuid: String): CompletableFuture<JsonObject?> =
        request("rewind_files") { addProperty("user_message_id", messageUuid) }

    fun stop() {
        control?.failAll("session stopped")
        process?.dispose()
        process = null
        control = null
    }

    override fun dispose() {
        stop()
    }

    private fun request(
        subtype: String,
        fill: JsonObject.() -> Unit = {},
    ): CompletableFuture<JsonObject?> {
        val channel = control
            ?: return CompletableFuture.failedFuture(IllegalStateException("session is not running"))
        return channel.request(subtype, fill)
    }

    private fun handleLine(line: String) {
        when (val frame = codec.parseLine(line)) {
            is IncomingFrame.Event -> {
                (frame.event as? StreamEvent.SessionStarted)?.let { sessionId = it.sessionId }
                listener.onEvent(frame.event)
            }

            is IncomingFrame.Permission -> listener.onPermission(frame.request) { decision ->
                process?.send(codec.controlResponse(frame.request.requestId, decision.toJson()))
            }

            is IncomingFrame.ControlAnswer -> control?.complete(frame)

            is IncomingFrame.ControlCancelled -> listener.onPermissionCancelled(frame.requestId)

            null -> Unit
        }
    }

    private fun handleTerminated(exitCode: Int) {
        control?.failAll("process terminated with exit code $exitCode")
        listener.onTerminated(exitCode)
    }

    private companion object {
        const val HANDSHAKE_TIMEOUT_SECONDS = 5L
    }
}
