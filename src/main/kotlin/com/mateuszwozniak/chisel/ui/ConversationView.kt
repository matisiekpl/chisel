package com.mateuszwozniak.chisel.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.mateuszwozniak.chisel.service.ConversationController
import java.util.concurrent.CopyOnWriteArrayList

fun interface ShownConversationListener {

    fun onConversationShown(controller: ConversationController)
}

@Service(Service.Level.PROJECT)
class ConversationView(private val project: Project) : Disposable {

    private val shownListeners = CopyOnWriteArrayList<ShownConversationListener>()

    var shown: ConversationController? = null
        private set

    private val panels = object : LinkedHashMap<String, ConversationPanel>(CACHE_SIZE, LOAD_FACTOR, true) {

        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ConversationPanel>): Boolean {
            if (size <= CACHE_SIZE) return false
            Disposer.dispose(eldest.value)
            return true
        }
    }

    fun open(controller: ConversationController) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return
        toolWindow.activate { show(toolWindow, controller) }
    }

    fun show(toolWindow: ToolWindow, controller: ConversationController) {
        val contentManager = toolWindow.contentManager
        val existing = contentManager.contents.firstOrNull { it.getUserData(CONTROLLER) === controller }
        if (existing != null) {
            contentManager.setSelectedContent(existing)
            focusInput(existing)
            announce(controller)
            return
        }
        val panel = panels.getOrPut(controller.conversation.id) { ConversationPanel(project, controller) }
        val content = ContentFactory.getInstance()
            .createContent(panel, controller.conversation.title, false)
        content.isCloseable = false
        content.putUserData(CONTROLLER, controller)
        content.preferredFocusableComponent = panel.focusTarget
        val replaced = contentManager.contents.toList()
        contentManager.addContent(content)
        contentManager.setSelectedContent(content)
        replaced.forEach { contentManager.removeContent(it, false) }
        focusInput(content)
        announce(controller)
    }

    fun addShownListener(listener: ShownConversationListener) {
        shownListeners.add(listener)
        shown?.let { listener.onConversationShown(it) }
    }

    fun removeShownListener(listener: ShownConversationListener) {
        shownListeners.remove(listener)
    }

    private fun announce(controller: ConversationController) {
        shown = controller
        shownListeners.forEach { it.onConversationShown(controller) }
    }

    fun close(controller: ConversationController) {
        val contentManager = ToolWindowManager.getInstance(project)
            .getToolWindow(TOOL_WINDOW_ID)
            ?.contentManagerIfCreated
        contentManager?.contents
            ?.firstOrNull { it.getUserData(CONTROLLER) === controller }
            ?.let { contentManager.removeContent(it, false) }
        release(controller)
    }

    fun release(controller: ConversationController) {
        panels.remove(controller.conversation.id)?.let { Disposer.dispose(it) }
    }

    fun retitle(controller: ConversationController) {
        ToolWindowManager.getInstance(project)
            .getToolWindow(TOOL_WINDOW_ID)
            ?.contentManagerIfCreated
            ?.contents
            ?.firstOrNull { it.getUserData(CONTROLLER) === controller }
            ?.displayName = controller.conversation.title
    }

    override fun dispose() {
        panels.values.forEach { Disposer.dispose(it) }
        panels.clear()
        shownListeners.clear()
    }

    private fun focusInput(content: Content) {
        val panel = content.component as? ConversationPanel ?: return
        ApplicationManager.getApplication().invokeLater { panel.focusInput() }
    }

    companion object {

        const val TOOL_WINDOW_ID = "Chisel"

        val CONTROLLER: Key<ConversationController> = Key.create("chisel.conversation.controller")

        private const val CACHE_SIZE = 5

        private const val LOAD_FACTOR = 0.75f

        fun getInstance(project: Project): ConversationView = project.service()
    }
}
