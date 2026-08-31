package com.mateuszwozniak.chisel.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.mateuszwozniak.chisel.model.TranscriptItem
import com.mateuszwozniak.chisel.service.ConversationController
import com.mateuszwozniak.chisel.service.ConversationListener
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.nio.file.Paths
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.SwingUtilities

class TranscriptPanel(
    private val project: Project,
    private val controller: ConversationController,
    private val onRewindRequested: (String, Boolean) -> Unit,
) : JPanel(BorderLayout()), ConversationListener, Disposable {

    private val html = TranscriptHtml(MarkdownRenderer(project), project.basePath)
    private val list = VerticalList(LIST_GAP)
    private val scroll = ScrollPaneFactory.createScrollPane(list, true)
    private val views = LinkedHashMap<String, TranscriptItemView>()
    private val queue = MergingUpdateQueue("ChiselTranscript", MERGE_MILLIS, true, list, this)
    private val busyIcon = AsyncProcessIcon("ChiselBusy")
    private val busyBar = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(4)))
    private val busyLabel = JBLabel(WORKING_TEXT).apply { foreground = UIUtil.getContextHelpForeground() }

    private var contentDisposable = Disposer.newDisposable("ChiselTranscriptContent")
    private var following = true

    init {
        list.isOpaque = false
        list.border = JBUI.Borders.empty(LIST_PADDING, 0)
        busyBar.isOpaque = false
        busyBar.border = JBUI.Borders.empty(8, 12, 10, 12)
        busyBar.add(busyIcon)
        busyBar.add(busyLabel)
        busyBar.isVisible = false

        add(scroll, BorderLayout.CENTER)
        add(busyBar, BorderLayout.SOUTH)

        scroll.addMouseWheelListener { event ->
            if (event.wheelRotation < 0) following = false
            else SwingUtilities.invokeLater { following = isScrolledToBottom() }
        }
        scroll.verticalScrollBar.addAdjustmentListener { event ->
            if (event.valueIsAdjusting) following = isScrolledToBottom()
        }

        Disposer.register(this, busyIcon)
        Disposer.register(this, contentDisposable)
        controller.addListener(this)
        rebuild()
        showBusy(controller.busy)
    }

    override fun onItemAdded(item: TranscriptItem) {
        queue.queue(object : Update("add-${item.id}") {
            override fun run() = mutate(item is TranscriptItem.UserPrompt) { addView(item) }
        })
    }

    override fun onItemUpdated(item: TranscriptItem) {
        queue.queue(object : Update("update-${item.id}") {
            override fun run() = mutate {
                val existing = views[item.id]
                if (existing == null) addView(item) else existing.refresh()
            }
        })
    }

    override fun onTranscriptReset() {
        queue.queue(object : Update("reset") {
            override fun run() = mutate { rebuild() }
        })
    }

    override fun onCompactingChanged(running: Boolean) {
        ApplicationManager.getApplication().invokeLater(
            { busyLabel.text = if (running) COMPACTING_TEXT else WORKING_TEXT },
            ModalityState.defaultModalityState(),
        )
    }

    override fun onBusyChanged(busy: Boolean) {
        ApplicationManager.getApplication().invokeLater(
            { showBusy(busy) },
            ModalityState.defaultModalityState(),
        )
    }

    override fun dispose() {
        controller.removeListener(this)
    }

    private fun showBusy(busy: Boolean) {
        busyBar.isVisible = busy
        if (busy) busyIcon.resume() else busyIcon.suspend()
        busyBar.revalidate()
        busyBar.repaint()
    }

    private fun rebuild() {
        following = true
        Disposer.dispose(contentDisposable)
        contentDisposable = Disposer.newDisposable("ChiselTranscriptContent")
        Disposer.register(this, contentDisposable)
        views.clear()
        list.removeAll()
        controller.conversation.transcript.toList().forEach { addView(it) }
    }

    private fun addView(item: TranscriptItem) {
        if (item is TranscriptItem.TurnSummary || item is TranscriptItem.Thinking) return
        val view = TranscriptItemView(html, item, contentDisposable)
        view.onRendered = { if (following) scrollToBottom() }
        when (item) {
            is TranscriptItem.UserPrompt -> {
                view.onContextMenu { component, x, y -> showPromptMenu(item, component, x, y) }
                view.onLinkClicked { link -> openAttachment(link) }
            }
            is TranscriptItem.ToolCall -> view.onClicked { showDetail(item) }
            else -> Unit
        }
        views[item.id] = view
        list.add(view)
    }

    private fun showPromptMenu(
        item: TranscriptItem.UserPrompt,
        component: Component,
        x: Int,
        y: Int,
    ) {
        val rewindable = item.messageUuid != null
        val actions = DefaultActionGroup(
            action("Copy", AllIcons.Actions.Copy, true) {
                CopyPasteManager.getInstance().setContents(StringSelection(item.text))
            },
            action("Edit", AllIcons.Actions.Edit, rewindable) {
                item.messageUuid?.let { onRewindRequested(it, true) }
            },
            action("Rewind", AllIcons.Actions.Rollback, rewindable) {
                item.messageUuid?.let { onRewindRequested(it, false) }
            },
        )
        ActionManager.getInstance()
            .createActionPopupMenu(PLACE, actions)
            .component
            .show(component, x, y)
    }

    private fun action(text: String, icon: Icon, enabled: Boolean, run: () -> Unit): AnAction =
        object : AnAction(text, null, icon), DumbAware {

            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

            override fun update(event: AnActionEvent) {
                event.presentation.isEnabled = enabled
            }

            override fun actionPerformed(event: AnActionEvent) = run()
        }

    private fun openAttachment(link: String) {
        val path = runCatching { Paths.get(URI(link)) }.getOrNull() ?: return
        FileOpener.open(project, path.toString())
    }

    private fun showDetail(item: TranscriptItem.ToolCall) {
        TranscriptDetailDialog(project, item.name, html.detail(item)).show()
    }

    private fun mutate(force: Boolean = false, action: () -> Unit) {
        if (force) following = true
        action()
        list.revalidate()
        list.repaint()
        if (following) SwingUtilities.invokeLater { scrollToBottom() }
    }

    private fun isScrolledToBottom(): Boolean {
        val bar = scroll.verticalScrollBar
        return bar.value + bar.visibleAmount >= bar.maximum - JBUI.scale(BOTTOM_TOLERANCE)
    }

    private fun scrollToBottom() {
        val bar = scroll.verticalScrollBar
        bar.value = bar.maximum
    }

    private companion object {
        const val PLACE = "ChiselTranscript"
        const val WORKING_TEXT = "Working…"
        const val COMPACTING_TEXT = "Compacting the conversation…"
        const val LIST_GAP = 6
        const val LIST_PADDING = 8
        const val MERGE_MILLIS = 50
        const val BOTTOM_TOLERANCE = 24
    }
}
