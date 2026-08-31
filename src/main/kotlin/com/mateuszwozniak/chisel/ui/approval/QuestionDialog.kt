package com.mateuszwozniak.chisel.ui.approval

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.mateuszwozniak.chisel.protocol.UserQuestion
import com.mateuszwozniak.chisel.ui.VerticalList
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Action
import javax.swing.ButtonGroup
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JToggleButton
import javax.swing.event.DocumentEvent

class QuestionDialog(
    project: Project,
    private val questions: List<UserQuestion>,
) : DialogWrapper(project, true) {

    enum class Outcome { ANSWER, DENY }

    private val sections = questions.map { QuestionSection(it) }

    private val answerAction = object : DialogWrapperAction("Send") {
        override fun doAction(event: ActionEvent) {
            setErrorText(null)
            val missing = sections.firstOrNull { it.answer() == null }
            if (missing != null) {
                setErrorText("Answer: " + missing.question.question)
                return
            }
            outcome = Outcome.ANSWER
            close(OK_EXIT_CODE)
        }
    }

    private val denyAction = object : DialogWrapperAction("Skip") {
        override fun doAction(event: ActionEvent) {
            outcome = Outcome.DENY
            close(CANCEL_EXIT_CODE)
        }
    }

    var outcome: Outcome = Outcome.DENY
        private set

    init {
        title = if (questions.size == 1) questions.first().header.ifEmpty { "Question" } else "Questions"
        init()
    }

    fun answers(): Map<String, String> = sections
        .mapNotNull { section -> section.answer()?.let { section.question.question to it } }
        .toMap()

    override fun createCenterPanel(): JComponent {
        val body = VerticalList(GAP)
        sections.forEach(body::add)
        val scroll = ScrollPaneFactory.createScrollPane(body, true)
        scroll.preferredSize = Dimension(JBUI.scale(WIDTH), JBUI.scale(HEIGHT))
        return scroll
    }

    override fun createActions(): Array<Action> = arrayOf(answerAction, denyAction)

    override fun getDimensionServiceKey(): String = "Chisel.Question"

    private class QuestionSection(val question: UserQuestion) : JPanel(VerticalLayout(JBUI.scale(SECTION_GAP))) {

        private val buttons = mutableListOf<Pair<JToggleButton, String>>()

        private val otherField = JBTextField()

        private val otherToggle: JToggleButton =
            if (question.multiSelect) JCheckBox(OTHER_LABEL) else JRadioButton(OTHER_LABEL)

        init {
            border = JBUI.Borders.emptyBottom(GAP)
            if (question.header.isNotEmpty()) {
                add(JBLabel(question.header).apply { foreground = UIUtil.getContextHelpForeground() })
            }
            add(JBLabel("<html><b>" + escape(question.question) + "</b></html>"))

            val group = if (question.multiSelect) null else ButtonGroup()
            question.options.forEach { option ->
                val button: JToggleButton =
                    if (question.multiSelect) JCheckBox(option.label) else JRadioButton(option.label)
                group?.add(button)
                buttons.add(button to option.label)
                add(button)
                option.description?.takeIf { it.isNotBlank() }?.let { description ->
                    add(indented(describe(description, button)))
                }
            }
            group?.add(otherToggle)
            add(otherToggle)
            otherField.emptyText.text = "Type your own answer"
            otherField.document.addDocumentListener(object : DocumentAdapter() {
                override fun textChanged(event: DocumentEvent) {
                    otherToggle.isSelected = true
                }
            })
            add(indented(otherField))
            if (!question.multiSelect) {
                buttons.firstOrNull()?.first?.isSelected = true
            }
        }

        fun answer(): String? {
            val chosen = buttons.filter { it.first.isSelected }.map { it.second }.toMutableList()
            if (otherToggle.isSelected) otherField.text.trim().takeIf { it.isNotEmpty() }?.let(chosen::add)
            if (chosen.isEmpty()) return null
            return chosen.joinToString(", ") { value ->
                if (value.contains(", ") || value.contains('"')) "\"" + value.replace("\"", "\\\"") + "\""
                else value
            }
        }

        private fun describe(text: String, button: JToggleButton): JBLabel =
            JBLabel("<html>" + escape(text) + "</html>").apply {
                foreground = UIUtil.getContextHelpForeground()
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(event: MouseEvent) = button.doClick()
                })
            }

        private fun indented(component: JComponent): JPanel {
            val wrapper = JPanel(BorderLayout())
            wrapper.border = JBUI.Borders.empty(0, INDENT, 4, 0)
            wrapper.add(component, BorderLayout.CENTER)
            return wrapper
        }

        private fun escape(text: String): String = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        private companion object {
            const val INDENT = 26
        }
    }

    private companion object {
        const val OTHER_LABEL = "Other"
        const val SECTION_GAP = 4
        const val WIDTH = 620
        const val HEIGHT = 420
        const val GAP = 10
    }
}
