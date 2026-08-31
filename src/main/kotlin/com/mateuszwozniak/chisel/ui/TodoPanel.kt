package com.mateuszwozniak.chisel.ui

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.mateuszwozniak.chisel.model.TodoItem
import com.mateuszwozniak.chisel.model.TodoStatus
import java.awt.BorderLayout
import javax.swing.JPanel

class TodoPanel : JPanel(BorderLayout()) {

    private val label = JBLabel()

    init {
        border = JBUI.Borders.empty(6, 8)
        add(label, BorderLayout.CENTER)
        isVisible = false
    }

    fun update(todos: List<TodoItem>) {
        if (todos.isEmpty()) {
            isVisible = false
            return
        }
        val rows = todos.joinToString("<br>") { marker(it.status) + " " + escape(it.content) }
        label.text = "<html><b>Plan</b><br>$rows</html>"
        isVisible = true
        revalidate()
        repaint()
    }

    private fun marker(status: TodoStatus): String = when (status) {
        TodoStatus.COMPLETED -> "[x]"
        TodoStatus.IN_PROGRESS -> "[&gt;]"
        TodoStatus.PENDING -> "[ ]"
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
