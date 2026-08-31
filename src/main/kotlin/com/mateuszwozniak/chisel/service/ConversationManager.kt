package com.mateuszwozniak.chisel.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.mateuszwozniak.chisel.model.AgentMode
import com.mateuszwozniak.chisel.model.AgentModel
import com.mateuszwozniak.chisel.model.Conversation
import com.mateuszwozniak.chisel.model.EffortLevel
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

    fun restore(): List<ConversationController> {
        if (restored) return conversations()
        restored = true
        ConversationState.getInstance(project).state.entries.toList().forEach { entry ->
            val id = entry.id ?: return@forEach
            val mode = AgentMode.fromPermissionMode(entry.mode.orEmpty())
            val settings = ChiselSettings.getInstance()
            val conversation = Conversation(
                id,
                entry.title ?: nextTitle(),
                mode,
                AgentModel.entries.firstOrNull { it.name == entry.model } ?: settings.modelFor(mode),
                EffortLevel.entries.firstOrNull { it.name == entry.effort } ?: settings.effortFor(mode),
                entry.sessionId,
            )
            conversation.transcript.addAll(store.load(id))
            register(conversation)
        }
        if (controllers.isEmpty()) create()
        return conversations()
    }

    fun create(): ConversationController {
        val settings = ChiselSettings.getInstance()
        val controller = register(
            Conversation(
                UUID.randomUUID().toString(),
                nextTitle(),
                AgentMode.PLAN,
                settings.modelFor(AgentMode.PLAN),
                settings.effortFor(AgentMode.PLAN),
            )
        )
        persist()
        notifyChanged()
        return controller
    }

    fun rename(controller: ConversationController, title: String) {
        controller.conversation.title = title
        persist()
        notifyChanged()
    }

    fun delete(controller: ConversationController) {
        if (controllers.remove(controller.conversation.id) == null) return
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
        state.entries = controllers.values.map { controller ->
            store.save(controller.conversation)
            ConversationEntry().apply {
                id = controller.conversation.id
                title = controller.conversation.title
                sessionId = controller.conversation.sessionId
                mode = controller.conversation.mode.permissionMode
                model = controller.conversation.model.name
                effort = controller.conversation.effort.name
            }
        }.toMutableList()
    }

    override fun dispose() {
        persist()
        controllers.clear()
        listeners.clear()
    }

    private fun register(conversation: Conversation): ConversationController {
        val controller = ConversationController(project, conversation, router)
        controllers[conversation.id] = controller
        Disposer.register(this, controller)
        controller.addListener(object : ConversationListener {
            override fun onBusyChanged(busy: Boolean) {
                if (!busy) store.save(conversation)
            }

            override fun onTitleChanged(title: String) {
                persist()
                notifyChanged()
            }
        })
        return controller
    }

    private fun notifyChanged() {
        listeners.forEach { it.onConversationsChanged() }
    }

    private fun nextTitle(): String {
        val used = controllers.values.mapNotNull {
            it.conversation.title.removePrefix(TITLE_PREFIX).toIntOrNull()
        }
        return TITLE_PREFIX + ((used.maxOrNull() ?: 0) + 1)
    }

    companion object {

        private const val TITLE_PREFIX = "Chat "

        fun getInstance(project: Project): ConversationManager = project.service()
    }
}
