package com.mateuszwozniak.chisel.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import com.mateuszwozniak.chisel.model.PlanTask
import com.mateuszwozniak.chisel.model.TodoStatus
import com.mateuszwozniak.chisel.service.ConversationController
import com.mateuszwozniak.chisel.service.ConversationListener
import java.awt.BorderLayout
import javax.swing.DefaultListModel
import javax.swing.Icon
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

class TasksPanel(private val project: Project) :
    JPanel(BorderLayout()), ConversationListener, ShownConversationListener, Disposable {

    private val model = DefaultListModel<PlanTask>()
    private val list = JBList(model)
    private var controller: ConversationController? = null

    init {
        list.cellRenderer = TaskRenderer()
        list.putClientProperty(AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, true)
        list.border = JBUI.Borders.empty(4, 8, 4, 4)
        list.emptyText.text = "No tasks in this conversation"
        val scroll = ScrollPaneFactory.createScrollPane(list, true)
        scroll.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        add(scroll, BorderLayout.CENTER)
        ConversationView.getInstance(project).addShownListener(this)
    }

    override fun onConversationShown(controller: ConversationController) {
        this.controller?.removeListener(this)
        this.controller = controller
        controller.addListener(this)
        refresh()
    }

    override fun onPlanTasksChanged() = onEventDispatchThread { refresh() }

    override fun onTranscriptReset() = onEventDispatchThread { refresh() }

    private fun onEventDispatchThread(action: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(action, ModalityState.defaultModalityState())
    }

    override fun dispose() {
        ConversationView.getInstance(project).removeShownListener(this)
        controller?.removeListener(this)
    }

    private fun refresh() {
        val tasks = controller?.conversation?.planTasks?.toList().orEmpty()
        model.clear()
        tasks.forEach(model::addElement)
    }

    companion object {

        const val TOOL_WINDOW_ID = "Tasks"
    }

    private class TaskRenderer : ColoredListCellRenderer<PlanTask>() {

        override fun customizeCellRenderer(
            list: JList<out PlanTask>,
            value: PlanTask,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean,
        ) {
            icon = statusIcon(value.status)
            append(value.subject)
            if (value.description.isNotBlank() && value.description != value.subject) {
                append("  " + value.description, SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
            toolTipText = value.description.takeIf { it.isNotBlank() }
        }

        private fun statusIcon(status: TodoStatus): Icon = when (status) {
            TodoStatus.COMPLETED -> AllIcons.Actions.Checked
            TodoStatus.IN_PROGRESS -> AnimatedIcon.Default.INSTANCE
            TodoStatus.PENDING -> AllIcons.General.TodoDefault
        }
    }
}
