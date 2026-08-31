package com.mateuszwozniak.chisel.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.mateuszwozniak.chisel.model.TranscriptItem
import com.mateuszwozniak.chisel.service.ConversationController
import com.mateuszwozniak.chisel.service.ConversationListener
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Rectangle
import javax.swing.JPanel
import javax.swing.Scrollable
import javax.swing.SwingUtilities

class TranscriptPanel(
    private val project: Project,
    private val controller: ConversationController,
    private val onRewindRequested: (String) -> Unit,
) : JPanel(BorderLayout()), ConversationListener, Disposable {

    private val html = TranscriptHtml(MarkdownRenderer(project), project.basePath)
    private val list = ItemList()
    private val scroll = ScrollPaneFactory.createScrollPane(list, true)
    private val views = LinkedHashMap<String, TranscriptItemView>()
    private val queue = MergingUpdateQueue("ChiselTranscript", MERGE_MILLIS, true, list, this)
    private val busyIcon = AsyncProcessIcon("ChiselBusy")
    private val busyBar = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(4)))

    private var contentDisposable = Disposer.newDisposable("ChiselTranscriptContent")

    init {
        list.isOpaque = false
        busyBar.isOpaque = false
        busyBar.border = JBUI.Borders.empty(0, 18, 4, 18)
        busyBar.add(busyIcon)
        busyBar.add(JBLabel("Working…").apply { foreground = UIUtil.getContextHelpForeground() })
        busyBar.isVisible = false

        add(scroll, BorderLayout.CENTER)
        add(busyBar, BorderLayout.SOUTH)

        Disposer.register(this, busyIcon)
        Disposer.register(this, contentDisposable)
        controller.addListener(this)
        rebuild()
        showBusy(controller.busy)
    }

    override fun onItemAdded(item: TranscriptItem) {
        queue.queue(object : Update("add-${item.id}") {
            override fun run() = mutate { addView(item) }
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
        Disposer.dispose(contentDisposable)
        contentDisposable = Disposer.newDisposable("ChiselTranscriptContent")
        Disposer.register(this, contentDisposable)
        views.clear()
        list.removeAll()
        controller.conversation.transcript.toList().forEach { addView(it) }
    }

    private fun addView(item: TranscriptItem) {
        if (item is TranscriptItem.TurnSummary) return
        val view = TranscriptItemView(html, item, contentDisposable)
        when (item) {
            is TranscriptItem.UserPrompt -> view.onClicked { item.messageUuid?.let(onRewindRequested) }
            is TranscriptItem.Thinking -> view.onClicked { showDetail("Thinking", item) }
            is TranscriptItem.ToolCall -> view.onClicked { showDetail(item.name, item) }
            else -> Unit
        }
        views[item.id] = view
        list.add(view)
    }

    private fun showDetail(title: String, item: TranscriptItem) {
        TranscriptDetailDialog(project, title, html.detail(item)).show()
    }

    private fun mutate(action: () -> Unit) {
        val atBottom = isScrolledToBottom()
        action()
        list.revalidate()
        list.repaint()
        if (atBottom) SwingUtilities.invokeLater { scrollToBottom() }
    }

    private fun isScrolledToBottom(): Boolean {
        val bar = scroll.verticalScrollBar
        return bar.value + bar.visibleAmount >= bar.maximum - JBUI.scale(BOTTOM_TOLERANCE)
    }

    private fun scrollToBottom() {
        val bar = scroll.verticalScrollBar
        bar.value = bar.maximum
    }

    private class ItemList : JPanel(VerticalLayout(JBUI.scale(6))), Scrollable {

        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

        override fun getScrollableUnitIncrement(visible: Rectangle, orientation: Int, direction: Int): Int =
            JBUI.scale(UNIT_INCREMENT)

        override fun getScrollableBlockIncrement(visible: Rectangle, orientation: Int, direction: Int): Int =
            visible.height

        override fun getScrollableTracksViewportWidth(): Boolean = true

        override fun getScrollableTracksViewportHeight(): Boolean = false

        private companion object {
            const val UNIT_INCREMENT = 16
        }
    }

    private companion object {
        const val MERGE_MILLIS = 50
        const val BOTTOM_TOLERANCE = 24
    }
}
