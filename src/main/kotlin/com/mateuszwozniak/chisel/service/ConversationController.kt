package com.mateuszwozniak.chisel.service

import com.google.gson.JsonObject
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.mateuszwozniak.chisel.cli.ClaudeCommandBuilder
import com.mateuszwozniak.chisel.cli.ClaudeExecutable
import com.mateuszwozniak.chisel.cli.ClaudeSession
import com.mateuszwozniak.chisel.cli.SessionListener
import com.mateuszwozniak.chisel.cli.SessionStart
import com.mateuszwozniak.chisel.model.AgentMode
import com.mateuszwozniak.chisel.model.AgentModel
import com.mateuszwozniak.chisel.model.EffortLevel
import com.mateuszwozniak.chisel.model.PromptAttachment
import com.mateuszwozniak.chisel.model.QueuedPrompt
import com.mateuszwozniak.chisel.model.Conversation
import com.mateuszwozniak.chisel.model.TodoItem
import com.mateuszwozniak.chisel.model.TodoStatus
import com.mateuszwozniak.chisel.model.TranscriptItem
import com.mateuszwozniak.chisel.protocol.ContentBlock
import com.mateuszwozniak.chisel.protocol.PermissionDecision
import com.mateuszwozniak.chisel.protocol.PermissionRequest
import com.mateuszwozniak.chisel.protocol.StreamEvent
import com.mateuszwozniak.chisel.protocol.WriteToolInput
import com.mateuszwozniak.chisel.protocol.string
import com.mateuszwozniak.chisel.state.ChiselSettings
import com.mateuszwozniak.chisel.util.VfsRefresh
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

class ConversationController(
    private val project: Project,
    val conversation: Conversation,
    private val permissionRouter: PermissionRouter,
) : SessionListener, Disposable {

    private val listeners = CopyOnWriteArrayList<ConversationListener>()
    private val queued = CopyOnWriteArrayList<QueuedPrompt>()
    private val streamingItems = mutableListOf<TranscriptItem>()
    private val toolCalls = mutableMapOf<String, TranscriptItem.ToolCall>()
    private val itemCounter = AtomicLong()
    private val standardError = StringBuilder()

    private var session: ClaudeSession? = null
    private var resumedSessionId: String? = null

    @Volatile
    var busy: Boolean = false
        private set

    fun addListener(listener: ConversationListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: ConversationListener) {
        listeners.remove(listener)
    }

    fun sendPrompt(text: String, attachments: List<PromptAttachment> = emptyList()) {
        if (busy) {
            queued.add(QueuedPrompt(text, attachments))
            notifyQueue()
            return
        }
        dispatch(QueuedPrompt(text, attachments))
    }

    fun queued(): List<QueuedPrompt> = queued.toList()

    fun dropQueued(prompt: QueuedPrompt) {
        if (queued.remove(prompt)) notifyQueue()
    }

    fun promptAt(index: Int): String? = conversation.transcript
        .filterIsInstance<TranscriptItem.UserPrompt>()
        .asReversed()
        .getOrNull(index)
        ?.text

    fun popLastQueued(): QueuedPrompt? {
        val last = queued.lastOrNull() ?: return null
        queued.remove(last)
        notifyQueue()
        return last
    }

    fun changeMode(mode: AgentMode) {
        conversation.mode = mode
        val settings = ChiselSettings.getInstance()
        conversation.model = settings.modelFor(mode)
        conversation.effort = settings.effortFor(mode)
        session?.takeIf { it.isRunning() }?.changeMode(mode)
        listeners.forEach { it.onOptionsChanged() }
    }

    fun changeModel(model: AgentModel) {
        conversation.model = model
        restartOnNextPrompt()
        listeners.forEach { it.onOptionsChanged() }
    }

    fun changeEffort(effort: EffortLevel) {
        conversation.effort = effort
        restartOnNextPrompt()
        listeners.forEach { it.onOptionsChanged() }
    }

    fun interrupt() {
        if (queued.isNotEmpty()) {
            queued.clear()
            notifyQueue()
        }
        session?.takeIf { it.isRunning() }?.interrupt()
    }

    fun rewind(messageUuid: String, onFinished: (String?) -> Unit) {
        val sessionIdentifier = conversation.sessionId
        val promptText = conversation.promptTextAt(messageUuid)
        val current = ensureSession()
        if (current == null || sessionIdentifier == null) {
            onFinished(null)
            return
        }
        current.rewindFiles(messageUuid).whenComplete { _, error ->
            if (error != null) appendNotice("Rewind of files failed: ${error.message}", true)
            synchronized(this) {
                conversation.truncateAt(messageUuid)
                streamingItems.clear()
                toolCalls.clear()
            }
            listeners.forEach { it.onTranscriptReset() }
            current.start(conversation.options(), SessionStart.ResumeAt(sessionIdentifier, messageUuid))
            VfsRefresh.refreshEverything()
            changeBusy(false)
            onFinished(promptText)
        }
    }

    fun filesTouchedAfter(messageUuid: String): List<String> {
        val index = conversation.transcript.indexOfFirst {
            it is TranscriptItem.UserPrompt && it.messageUuid == messageUuid
        }
        if (index < 0) return emptyList()
        return conversation.transcript
            .drop(index)
            .filterIsInstance<TranscriptItem.ToolCall>()
            .filter { it.name in WriteToolInput.TOOL_NAMES && it.result != null && !it.failed }
            .mapNotNull { it.input.string("file_path") ?: it.input.string("notebook_path") }
            .distinct()
    }

    fun messagesDroppedAfter(messageUuid: String): Int {
        val index = conversation.transcript.indexOfFirst {
            it is TranscriptItem.UserPrompt && it.messageUuid == messageUuid
        }
        return if (index < 0) 0 else conversation.transcript.size - index
    }

    override fun onEvent(event: StreamEvent) {
        when (event) {
            is StreamEvent.SessionStarted -> adoptSession(event.sessionId)
            is StreamEvent.TextDelta -> appendTextDelta(event.text)
            is StreamEvent.ThinkingDelta -> appendThinkingDelta(event.text)
            is StreamEvent.AssistantTurn -> applyAssistantTurn(event)
            is StreamEvent.UserTurn -> applyUserTurn(event)
            is StreamEvent.ApiRetry -> appendNotice(
                "Retry ${event.attempt} of ${event.maxRetries}: ${event.error}",
                false,
            )

            is StreamEvent.TurnFinished -> applyTurnFinished(event)
        }
    }

    override fun onPermission(request: PermissionRequest, respond: (PermissionDecision) -> Unit) {
        permissionRouter.handle(this, request, respond)
    }

    override fun onPermissionCancelled(requestId: String) {
        permissionRouter.cancel(requestId)
    }

    override fun onStandardError(text: String) {
        synchronized(standardError) {
            standardError.append(text)
            if (standardError.length > STANDARD_ERROR_LIMIT) {
                standardError.delete(0, standardError.length - STANDARD_ERROR_LIMIT)
            }
        }
    }

    override fun onTerminated(exitCode: Int) {
        changeBusy(false)
        if (exitCode == 0) return
        val details = synchronized(standardError) { standardError.toString().trim() }
        appendNotice("Claude Code exited with code $exitCode. $details".trim(), true)
    }

    override fun dispose() {
        session?.dispose()
        session = null
        listeners.clear()
    }

    private fun adoptSession(sessionId: String) {
        val requested = resumedSessionId
        resumedSessionId = null
        conversation.sessionId = sessionId
        listeners.forEach { it.onSessionStarted() }
        if (requested != null && requested != sessionId) {
            appendNotice(
                "Could not resume the previous Claude session, so this turn starts without earlier context.",
                true,
            )
        }
    }

    private fun restartOnNextPrompt() {
        session?.takeIf { it.isRunning() }?.stop()
    }

    private fun ensureSession(): ClaudeSession? {
        session?.takeIf { it.isRunning() }?.let { return it }
        val executable = ClaudeExecutable.locate() ?: run {
            appendNotice(ClaudeExecutable.missingMessage(), true)
            return null
        }
        val builder = ClaudeCommandBuilder(
            executable,
            project.basePath ?: System.getProperty("user.dir"),
        )
        val created = session ?: ClaudeSession(builder, this).also {
            session = it
            Disposer.register(this, it)
        }
        val identifier = conversation.sessionId
        val start = if (identifier == null) SessionStart.Fresh(UUID.randomUUID().toString())
        else SessionStart.Resume(identifier)
        resumedSessionId = identifier
        return try {
            created.start(conversation.options(), start)
            created
        } catch (failure: Exception) {
            appendNotice("Could not start Claude Code: " + failure.message, true)
            null
        }
    }

    private fun applyAssistantTurn(event: StreamEvent.AssistantTurn) {
        val pending = ArrayDeque<TranscriptItem>(streamingItems)
        streamingItems.clear()
        event.blocks.forEach { block ->
            when (block) {
                is ContentBlock.Text -> settleText(pending, block.text)

                is ContentBlock.Thinking -> settleThinking(pending, block.text)

                is ContentBlock.ToolUse -> appendToolCall(block, event.parentToolUseId)

                is ContentBlock.ToolResult -> Unit
            }
        }
        discard(pending)
    }

    private fun settleText(pending: ArrayDeque<TranscriptItem>, text: String) {
        val existing = takeMatching<TranscriptItem.AssistantText>(pending)
        if (existing != null) {
            existing.text = text
            listeners.forEach { it.onItemUpdated(existing) }
            return
        }
        if (text.isNotBlank()) appendItem(TranscriptItem.AssistantText(nextId(), text))
    }

    private fun settleThinking(pending: ArrayDeque<TranscriptItem>, text: String) {
        val existing = takeMatching<TranscriptItem.Thinking>(pending)
        if (existing != null) {
            existing.text = text
            listeners.forEach { it.onItemUpdated(existing) }
            return
        }
        if (text.isNotBlank()) appendItem(TranscriptItem.Thinking(nextId(), text))
    }

    private inline fun <reified T : TranscriptItem> takeMatching(
        pending: ArrayDeque<TranscriptItem>,
    ): T? {
        val head = pending.firstOrNull()
        if (head !is T) return null
        pending.removeFirst()
        return head
    }

    private fun appendToolCall(block: ContentBlock.ToolUse, parentToolUseId: String?) {
        val call = TranscriptItem.ToolCall(nextId(), block.id, block.name, block.input, parentToolUseId)
        synchronized(this) { toolCalls[block.id] = call }
        appendItem(call)
        if (block.name == TODO_TOOL) applyTodos(block.input)
    }

    private fun applyUserTurn(event: StreamEvent.UserTurn) {
        val results = event.blocks.filterIsInstance<ContentBlock.ToolResult>()
        if (results.isNotEmpty()) {
            results.forEach { result ->
                val call = synchronized(this) { toolCalls[result.toolUseId] } ?: return@forEach
                call.result = result.text
                call.failed = result.isError
                listeners.forEach { it.onItemUpdated(call) }
            }
            return
        }
        val uuid = event.uuid ?: return
        val prompt = synchronized(this) {
            conversation.transcript
                .filterIsInstance<TranscriptItem.UserPrompt>()
                .firstOrNull { it.messageUuid == null }
        } ?: return
        prompt.messageUuid = uuid
        listeners.forEach { it.onItemUpdated(prompt) }
    }

    private fun applyTurnFinished(event: StreamEvent.TurnFinished) {
        dropStreamingItems()
        event.errorText?.let { appendNotice(it, true) }
        appendItem(
            TranscriptItem.TurnSummary(
                nextId(),
                event.costUsd,
                event.inputTokens,
                event.outputTokens,
            )
        )
        changeBusy(false)
    }

    private fun applyTodos(input: JsonObject) {
        val entries = input.getAsJsonArray("todos") ?: return
        synchronized(this) {
            conversation.todos.clear()
            entries.mapNotNull { it as? JsonObject }.forEach { entry ->
                conversation.todos.add(
                    TodoItem(
                        entry.string("content").orEmpty(),
                        TodoStatus.fromWireName(entry.string("status")),
                    )
                )
            }
        }
        val snapshot = synchronized(this) { conversation.todos.toList() }
        listeners.forEach { it.onTodosChanged(snapshot) }
    }

    private fun appendTextDelta(text: String) {
        val existing = streamingItems.lastOrNull() as? TranscriptItem.AssistantText
        if (existing != null) {
            existing.text += text
            listeners.forEach { it.onItemUpdated(existing) }
            return
        }
        val item = TranscriptItem.AssistantText(nextId(), text)
        streamingItems.add(item)
        appendItem(item)
    }

    private fun appendThinkingDelta(text: String) {
        val existing = streamingItems.lastOrNull() as? TranscriptItem.Thinking
        if (existing != null) {
            existing.text += text
            listeners.forEach { it.onItemUpdated(existing) }
            return
        }
        val item = TranscriptItem.Thinking(nextId(), text)
        streamingItems.add(item)
        appendItem(item)
    }

    private fun dropStreamingItems() {
        val leftovers = streamingItems.toList()
        streamingItems.clear()
        discard(leftovers)
    }

    private fun discard(items: Collection<TranscriptItem>) {
        if (items.isEmpty()) return
        synchronized(this) { conversation.transcript.removeAll(items.toSet()) }
        listeners.forEach { it.onTranscriptReset() }
    }

    private fun appendNotice(text: String, failed: Boolean) {
        appendItem(TranscriptItem.Notice(nextId(), text, failed))
    }

    private fun appendItem(item: TranscriptItem) {
        synchronized(this) { conversation.transcript.add(item) }
        listeners.forEach { it.onItemAdded(item) }
    }

    private fun changeBusy(value: Boolean) {
        if (busy == value) return
        busy = value
        listeners.forEach { it.onBusyChanged(value) }
        if (!value) drainQueue()
    }

    private fun drainQueue() {
        val next = queued.removeFirstOrNull() ?: return
        notifyQueue()
        dispatch(next)
    }

    private fun dispatch(prompt: QueuedPrompt) {
        val target = ensureSession() ?: return
        val first = conversation.transcript.none { it is TranscriptItem.UserPrompt }
        appendItem(
            TranscriptItem.UserPrompt(
                nextId(),
                prompt.text,
                attachments = prompt.attachments.map { it.path },
            )
        )
        if (first) adoptTitle(prompt.text)
        changeBusy(true)
        target.prompt(prompt.text, prompt.attachments)
    }

    private fun notifyQueue() {
        val snapshot = queued.toList()
        listeners.forEach { it.onQueueChanged(snapshot) }
    }

    private fun adoptTitle(text: String) {
        val line = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: return
        conversation.title = if (line.length > TITLE_LIMIT) {
            line.take(TITLE_LIMIT).trimEnd() + "\u2026"
        } else {
            line
        }
        listeners.forEach { it.onTitleChanged(conversation.title) }
    }

    private fun nextId(): String = "item-${itemCounter.incrementAndGet()}"

    private companion object {
        const val TODO_TOOL = "TodoWrite"
        const val STANDARD_ERROR_LIMIT = 4000
        const val TITLE_LIMIT = 48
    }
}
