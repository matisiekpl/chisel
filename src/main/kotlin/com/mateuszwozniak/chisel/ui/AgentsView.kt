package com.mateuszwozniak.chisel.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.mateuszwozniak.chisel.model.AgentTask
import com.mateuszwozniak.chisel.model.TranscriptItem
import com.mateuszwozniak.chisel.service.ConversationController
import com.mateuszwozniak.chisel.service.ConversationListener
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class AgentsView(
    private val project: Project,
    private val controller: ConversationController,
    private val html: TranscriptHtml,
) : JPanel(BorderLayout()), ConversationListener, Disposable {

    private val root = DefaultMutableTreeNode()
    private val treeModel = DefaultTreeModel(root)
    private val tree = Tree(treeModel)
    private val queue = MergingUpdateQueue("ChiselAgents", MERGE_MILLIS, true, this, this)

    init {
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.cellRenderer = AgentTreeRenderer(html)
        tree.putClientProperty(AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, true)
        tree.border = JBUI.Borders.empty(4, 8, 4, 4)
        tree.emptyText.text = "No agents in this conversation"
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (!SwingUtilities.isLeftMouseButton(event) || event.clickCount < 2) return
                openDetail()
            }
        })

        val scroll = ScrollPaneFactory.createScrollPane(tree, true)
        scroll.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        add(scroll, BorderLayout.CENTER)

        controller.addListener(this)
        rebuild()
    }

    override fun onItemAdded(item: TranscriptItem) = scheduleRebuild()

    override fun onItemUpdated(item: TranscriptItem) = scheduleRebuild()

    override fun onTranscriptReset() = scheduleRebuild()

    override fun onTasksChanged() = scheduleRebuild()

    override fun dispose() {
        controller.removeListener(this)
    }

    private fun scheduleRebuild() {
        queue.queue(object : Update("rebuild") {
            override fun run() = rebuild()
        })
    }

    private fun rebuild() {
        root.removeAllChildren()
        val transcript = controller.conversation.transcript.toList()
        val turn = controller.conversation.turn()
        controller.conversation.tasks
            .filter { !it.finished || it.turn >= turn }
            .forEach { task ->
                val node = DefaultMutableTreeNode(task)
                transcript.filter { parentOf(it) == task.toolUseId }
                    .forEach { child -> node.add(DefaultMutableTreeNode(child)) }
                root.add(node)
            }
        treeModel.reload()
        expandAll()
    }

    private fun expandAll() {
        (0 until root.childCount).forEach { index ->
            tree.expandPath(TreePath(arrayOf<Any>(root, root.getChildAt(index))))
        }
    }

    private fun openDetail() {
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
        when (val item = node.userObject) {
            is AgentTask -> showTask(item)
            is TranscriptItem.ToolCall -> TranscriptDetailDialog(project, item.name, html.detail(item)).show()
            else -> Unit
        }
    }

    private fun showTask(task: AgentTask) {
        val title = task.subagentType?.let { it + " · " + task.description } ?: task.description
        TranscriptDetailDialog(project, title, html.markdown(outputOf(task))).show()
    }

    private fun outputOf(task: AgentTask): String {
        val fromFile = task.outputFile
            ?.let { runCatching { Files.readString(Paths.get(it)) }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
        if (fromFile != null) return fromFile
        val collected = controller.conversation.transcript
            .filterIsInstance<TranscriptItem.AssistantText>()
            .filter { it.parentToolUseId == task.toolUseId }
            .joinToString("\n\n") { it.text }
        return collected.ifBlank { "This agent has not reported anything yet." }
    }

    private fun parentOf(item: TranscriptItem): String? = when (item) {
        is TranscriptItem.AssistantText -> item.parentToolUseId
        is TranscriptItem.ToolCall -> item.parentToolUseId
        else -> null
    }

    private class AgentTreeRenderer(private val html: TranscriptHtml) : ColoredTreeCellRenderer() {

        override fun customizeCellRenderer(
            tree: JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ) {
            when (val item = (value as? DefaultMutableTreeNode)?.userObject) {
                is AgentTask -> {
                    icon = statusIcon(item)
                    append(item.subagentType ?: "agent")
                    append("  " + html.shorten(item.description), SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    append("  " + item.status, SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }

                is TranscriptItem.ToolCall -> {
                    icon = AllIcons.General.Balloon
                    append(item.name)
                    val summary = html.toolSummary(item.name, item.input)
                    if (summary.isNotEmpty()) {
                        append("  " + html.shorten(summary), SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    }
                }

                is TranscriptItem.AssistantText -> {
                    icon = AllIcons.General.Note
                    append(html.shorten(item.text), SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }

                else -> Unit
            }
        }

        private fun statusIcon(task: AgentTask): Icon = when {
            !task.finished -> AnimatedIcon.Default.INSTANCE
            task.status == COMPLETED -> AllIcons.Actions.Checked
            else -> AllIcons.General.Error
        }
    }

    private companion object {
        const val MERGE_MILLIS = 100
        const val COMPLETED = "completed"
    }
}
