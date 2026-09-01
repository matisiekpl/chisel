package com.mateuszwozniak.chisel.service

import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.mateuszwozniak.chisel.cli.ClaudeSessions
import com.mateuszwozniak.chisel.model.AgentMode
import com.mateuszwozniak.chisel.model.AgentModel
import com.mateuszwozniak.chisel.model.Conversation
import com.mateuszwozniak.chisel.model.EffortLevel
import com.mateuszwozniak.chisel.model.TerminalSession
import com.mateuszwozniak.chisel.model.TranscriptItem
import com.mateuszwozniak.chisel.state.ChiselSettings
import com.mateuszwozniak.chisel.state.ConversationEntry
import com.mateuszwozniak.chisel.state.ConversationState
import com.mateuszwozniak.chisel.ui.approval.ApprovalDialogs
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.PROJECT)
class ConversationManager(private val project: Project) : Disposable {

    private val router: PermissionRouter = ApprovalDialogs(project)
    private val store = TranscriptStore(project)
    private val controllers = LinkedHashMap<String, ConversationController>()
    private val listeners = CopyOnWriteArrayList<ConversationsListener>()
    private var restored = false

    fun conversations(): List<ConversationController> = controllers.values.toList()

    fun started(): List<ConversationController> = controllers.values
        .filter { it.conversation.transcript.isNotEmpty() }
        .sortedByDescending { it.conversation.updatedAt }

    fun restore(): List<ConversationController> {
        if (restored) return conversations()
        restored = true
        ConversationState.getInstance(project).state.entries.toList().forEach { entry ->
            val id = entry.id ?: return@forEach
            val mode = AgentMode.entries.firstOrNull { it.name == entry.mode }
                ?: AgentMode.fromPermissionMode(entry.mode.orEmpty())
            val settings = ChiselSettings.getInstance()
            val conversation = Conversation(
                id,
                titleOf(entry),
                mode,
                AgentModel.entries.firstOrNull { it.name == entry.model } ?: settings.modelFor(mode),
                EffortLevel.entries.firstOrNull { it.name == entry.effort } ?: settings.effortFor(mode),
                entry.sessionId,
            )
            conversation.transcript.addAll(store.load(id))
            entry.updatedAt.takeIf { it > 0 }?.let { conversation.updatedAt = it }
            register(conversation)
        }
        if (controllers.isEmpty()) create()
        importTerminalSessions()
        return conversations()
    }

    private fun titleOf(entry: ConversationEntry): String =
        entry.title?.takeIf { !ClaudeSessions.isGenerated(it) }
            ?: entry.sessionId?.let { ClaudeSessions.promptTitleOf(project.basePath, it) }
            ?: nextTitle()

    fun importTerminalSessions() {
        val known = knownSessions()
        ApplicationManager.getApplication().executeOnPooledThread {
            val read = ClaudeSessions.list(project.basePath)
                .filterNot { it.id in known }
                .map { it to ClaudeSessions.transcriptOf(project.basePath, it.id) }
                .filter { it.second.isNotEmpty() }
            if (read.isEmpty()) return@executeOnPooledThread
            ApplicationManager.getApplication().invokeLater({ adopt(read) }, project.disposed)
        }
    }

    private fun adopt(sessions: List<Pair<TerminalSession, List<TranscriptItem>>>) {
        val known = knownSessions()
        val settings = ChiselSettings.getInstance()
        val imported = sessions.filterNot { it.first.id in known }
        if (imported.isEmpty()) return
        val adopted = imported.map { (session, transcript) ->
            val mode = settings.defaultMode
            val conversation = Conversation(
                UUID.randomUUID().toString(),
                session.title,
                mode,
                settings.modelFor(mode),
                settings.effortFor(mode),
                session.id,
            )
            conversation.transcript.addAll(transcript)
            conversation.updatedAt = session.modifiedAt.toEpochMilli()
            register(conversation)
            conversation
        }
        persist()
        notifyChanged()
        ApplicationManager.getApplication().executeOnPooledThread {
            adopted.forEach { store.save(it) }
        }
    }

    private fun knownSessions(): Set<String> =
        controllers.values.mapNotNull { it.conversation.sessionId }.toSet() +
            ConversationState.getInstance(project).state.dismissedSessions.toSet()

    fun create(): ConversationController = create(null, nextTitle())

    fun createOrReuse(): ConversationController =
        controllers.values.firstOrNull { it.conversation.transcript.isEmpty() } ?: create()

    fun create(sessionId: String?, title: String): ConversationController {
        val settings = ChiselSettings.getInstance()
        val mode = settings.defaultMode
        val conversation = Conversation(
            UUID.randomUUID().toString(),
            title,
            mode,
            settings.modelFor(mode),
            settings.effortFor(mode),
            sessionId,
        )
        if (sessionId != null) {
            conversation.transcript.addAll(ClaudeSessions.transcriptOf(project.basePath, sessionId))
            store.save(conversation)
        }
        val controller = register(conversation)
        persist()
        notifyChanged()
        return controller
    }

    fun rename(controller: ConversationController, title: String) {
        controller.conversation.title = title
        controller.conversation.titleLocked = true
        persist()
        notifyChanged()
    }

    fun delete(controller: ConversationController) {
        if (controllers.remove(controller.conversation.id) == null) return
        controller.conversation.sessionId?.let(::dismiss)
        store.delete(controller.conversation.id)
        Disposer.dispose(controller)
        persist()
        notifyChanged()
    }

    fun addChangeListener(listener: ConversationsListener) {
        listeners.add(listener)
    }

    fun removeChangeListener(listener: ConversationsListener) {
        listeners.remove(listener)
    }

    fun persist() {
        val state = ConversationState.getInstance(project).state
        state.entries = controllers.values.filter { it.conversation.transcript.isNotEmpty() }.map { controller ->
            ConversationEntry().apply {
                id = controller.conversation.id
                title = controller.conversation.title
                sessionId = controller.conversation.sessionId
                mode = controller.conversation.mode.name
                model = controller.conversation.model.name
                effort = controller.conversation.effort.name
                updatedAt = controller.conversation.updatedAt
            }
        }.toMutableList()
        SaveAndSyncHandler.getInstance().scheduleProjectSave(project)
    }

    override fun dispose() {
        controllers.values.forEach { store.save(it.conversation) }
        persist()
        controllers.clear()
        listeners.clear()
    }

    private fun dismiss(sessionId: String) {
        val dismissed = ConversationState.getInstance(project).state.dismissedSessions
        if (!dismissed.contains(sessionId)) dismissed.add(sessionId)
    }

    private fun register(conversation: Conversation): ConversationController {
        val controller = ConversationController(project, conversation, router)
        controllers[conversation.id] = controller
        Disposer.register(this, controller)
        controller.addListener(object : ConversationListener {
            override fun onBusyChanged(busy: Boolean) {
                if (busy) return
                store.save(conversation)
                persist()
                notifyChanged()
            }

            override fun onTitleChanged(title: String) {
                persist()
                notifyChanged()
            }

            override fun onSessionStarted() = persist()
        })
        return controller
    }

    private fun notifyChanged() {
        listeners.forEach { it.onConversationsChanged() }
    }

    private fun nextTitle(): String = NEW_TITLE

    companion object {

        private const val NEW_TITLE = "New chat"

        fun getInstance(project: Project): ConversationManager = project.service()
    }
}
