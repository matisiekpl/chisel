package com.mateuszwozniak.chisel.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.mateuszwozniak.chisel.cli.ClaudeSessions
import com.mateuszwozniak.chisel.service.ConversationController
import com.mateuszwozniak.chisel.service.ConversationManager
import com.mateuszwozniak.chisel.service.ConversationsListener
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

class ConversationListPanel(private val project: Project) :
    SimpleToolWindowPanel(true, true), ConversationsListener, Disposable {

    private val manager = ConversationManager.getInstance(project)
    private val root = DefaultMutableTreeNode()
    private val group = DefaultMutableTreeNode(GROUP_LABEL)
    private val treeModel = DefaultTreeModel(root)
    private val tree = Tree(treeModel)
    private val searchField = SearchTextField(false)

    private val renameAction = object :
        AnAction("Rename", "Rename this conversation", AllIcons.Actions.Edit), DumbAware {

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = selected().size == 1
        }

        override fun actionPerformed(event: AnActionEvent) = rename()
    }

    private val deleteAction = object :
        AnAction("Delete", "Delete this conversation", AllIcons.Actions.GC), DumbAware {

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = selected().isNotEmpty()
        }

        override fun actionPerformed(event: AnActionEvent) = delete()
    }

    init {
        root.add(group)
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.selectionModel.selectionMode = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
        tree.cellRenderer = ConversationTreeRenderer()
        tree.emptyText.text = "No conversations"
        tree.border = JBUI.Borders.empty(4, 8, 0, 0)
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (!SwingUtilities.isLeftMouseButton(event)) return
                if (event.isShiftDown || event.isControlDown || event.isMetaDown) return
                selected().singleOrNull()?.let { ConversationTabs.open(project, it) }
            }
        })
        tree.addMouseListener(object : PopupHandler() {
            override fun invokePopup(component: Component, x: Int, y: Int) {
                if (selected().isEmpty()) return
                ActionManager.getInstance()
                    .createActionPopupMenu(PLACE, contextMenu())
                    .component
                    .show(component, x, y)
            }
        })

        searchField.textEditor.emptyText.text = "Search conversations"
        searchField.border = JBUI.Borders.empty(4, 6)
        searchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) = refresh()
        })

        toolbar = buildToolbar()
        setContent(buildContent())

        manager.addChangeListener(this)
        refresh()
    }

    override fun onConversationsChanged() = refresh()

    override fun dispose() {
        manager.removeChangeListener(this)
    }

    private fun buildContent(): JComponent {
        val content = JPanel(BorderLayout())
        content.add(searchField, BorderLayout.NORTH)
        content.add(ScrollPaneFactory.createScrollPane(tree, true), BorderLayout.CENTER)
        return content
    }

    private fun buildToolbar(): JComponent {
        val actions = DefaultActionGroup(
            NewConversationAction(project),
            renameAction,
            deleteAction,
            Separator.getInstance(),
            AccountAction(project),
        )
        val toolbar = ActionManager.getInstance().createActionToolbar(PLACE, actions, true)
        toolbar.targetComponent = tree
        return toolbar.component
    }

    private fun contextMenu(): ActionGroup = DefaultActionGroup(renameAction, deleteAction)

    private fun rename() {
        val controller = selected().singleOrNull() ?: return
        val title = Messages.showInputDialog(
            project,
            "Conversation name:",
            "Rename Conversation",
            null,
            controller.conversation.title,
            null,
        )?.trim()
        if (title.isNullOrEmpty()) return
        manager.rename(controller, title)
        ConversationTabs.retitle(project, controller)
    }

    private fun delete() {
        val controllers = selected()
        if (controllers.isEmpty()) return
        val resumable = controllers.any { it.conversation.sessionId != null }
        var removeSessions = false
        val confirmed = if (!resumable) {
            Messages.showYesNoDialog(
                project,
                deleteMessage(controllers),
                deleteTitle(controllers),
                Messages.getWarningIcon(),
            ) == Messages.YES
        } else {
            Messages.showCheckboxMessageDialog(
                deleteMessage(controllers),
                deleteTitle(controllers),
                arrayOf("Delete", "Cancel"),
                "Also delete the Claude CLI session files",
                false,
                0,
                1,
                Messages.getWarningIcon(),
            ) { exitCode, checkbox ->
                removeSessions = checkbox.isSelected
                exitCode
            } == 0
        }
        if (!confirmed) return
        controllers.forEach { controller ->
            ConversationTabs.close(project, controller)
            if (removeSessions) {
                controller.conversation.sessionId?.let { ClaudeSessions.delete(project.basePath, it) }
            }
            manager.delete(controller)
        }
    }

    private fun deleteMessage(controllers: List<ConversationController>): String {
        val single = controllers.singleOrNull()
        if (single == null) return "Delete " + controllers.size + " conversations and their transcripts?"
        return "Delete " + single.conversation.title + " and its transcript?"
    }

    private fun deleteTitle(controllers: List<ConversationController>): String =
        if (controllers.size == 1) "Delete Conversation" else "Delete Conversations"

    private fun refresh() {
        val previous = selected()
        val filter = searchField.text.trim()
        group.removeAllChildren()
        manager.started()
            .filter { filter.isEmpty() || it.conversation.title.contains(filter, ignoreCase = true) }
            .forEach { group.add(DefaultMutableTreeNode(it)) }
        treeModel.reload()
        tree.expandPath(TreePath(arrayOf<Any>(root, group)))
        select(previous)
    }

    private fun select(controllers: List<ConversationController>) {
        if (controllers.isEmpty()) return
        val paths = (0 until group.childCount)
            .map { group.getChildAt(it) }
            .filterIsInstance<DefaultMutableTreeNode>()
            .filter { it.userObject in controllers }
            .map { TreePath(it.path) }
        if (paths.isNotEmpty()) tree.selectionPaths = paths.toTypedArray()
    }

    private fun selected(): List<ConversationController> = tree.selectionPaths
        .orEmpty()
        .mapNotNull { (it.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? ConversationController }

    private class ConversationTreeRenderer : ColoredTreeCellRenderer() {

        override fun customizeCellRenderer(
            tree: JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ) {
            when (val node = (value as? DefaultMutableTreeNode)?.userObject) {
                is ConversationController -> {
                    icon = AllIcons.General.Balloon
                    append(node.conversation.title)
                }

                is String -> append(node, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
            }
        }
    }

    private companion object {
        const val PLACE = "ChiselConversationList"
        const val GROUP_LABEL = "Conversations"
    }
}
