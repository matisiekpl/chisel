package com.mateuszwozniak.chisel.ui.approval

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapper.IdeModalityType
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.mateuszwozniak.chisel.protocol.UserQuestion
import com.mateuszwozniak.chisel.ui.Shortcuts
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.ItemEvent
import java.awt.event.KeyEvent
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
import javax.swing.text.JTextComponent

class QuestionDialog(
    project: Project,
    private val questions: List<UserQuestion>,
) : DialogWrapper(project, true, IdeModalityType.MODELESS) {

    enum class Outcome { ANSWER, DENY }

    private val sections = questions.map { QuestionSection(it) }

    private val progress = JBLabel().apply { foreground = UIUtil.getContextHelpForeground() }

    private val body = JPanel(BorderLayout())

    private val scroll = ScrollPaneFactory.createScrollPane(body, true)

    private var index = 0

    private val answerAction = object : DialogWrapperAction(sendLabel()) {
        override fun doAction(event: ActionEvent) = advance()
    }

    private val backAction =
        object : DialogWrapperAction(Shortcuts.labelled("Back", "←")) {
            override fun doAction(event: ActionEvent) = goBack()
        }

    private val denyAction = object : DialogWrapperAction(Shortcuts.labelled("Skip", Shortcuts.ESCAPE_LABEL)) {
        override fun doAction(event: ActionEvent) = doCancelAction()
    }

    var outcome: Outcome = Outcome.DENY
        private set

    init {
        title = if (questions.size == 1) questions.first().header.ifEmpty { "Question" } else "Questions"
        answerAction.putValue(DEFAULT_ACTION, true)
        init()
        Shortcuts.install(rootPane, Shortcuts.submit()) { advance() }
        Shortcuts.install(rootPane, Shortcuts.digits()) { selectByDigit(it) }
        Shortcuts.install(rootPane, Shortcuts.arrow(KeyEvent.VK_LEFT)) { arrow(it, -1) }
        Shortcuts.install(rootPane, Shortcuts.arrow(KeyEvent.VK_RIGHT)) { arrow(it, 1) }
        showQuestion(0)
    }

    override fun doCancelAction() {
        outcome = Outcome.DENY
        super.doCancelAction()
    }

    override fun getPreferredFocusedComponent(): JComponent? = sections.firstOrNull()?.firstOption()

    fun answers(): Map<String, String> = sections
        .mapNotNull { section -> section.answer()?.let { section.question.question to it } }
        .toMap()

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, JBUI.scale(GAP)))
        panel.add(progress, BorderLayout.NORTH)
        panel.add(scroll, BorderLayout.CENTER)
        return panel
    }

    override fun createActions(): Array<Action> = arrayOf(denyAction, answerAction)

    override fun createLeftSideActions(): Array<Action> = arrayOf(backAction)

    private fun showQuestion(target: Int) {
        index = target
        body.removeAll()
        body.add(sections[index], BorderLayout.NORTH)
        progress.text = if (questions.size == 1) "" else "Question " + (index + 1) + " of " + questions.size
        progress.isVisible = questions.size > 1
        answerAction.putValue(Action.NAME, if (isLast()) sendLabel() else nextLabel())
        getButton(backAction)?.isVisible = index > 0
        scroll.preferredSize = Dimension(
            JBUI.scale(WIDTH),
            (body.preferredSize.height + JBUI.scale(PADDING)).coerceAtMost(JBUI.scale(MAX_HEIGHT)),
        )
        body.revalidate()
        body.repaint()
        pack()
        sections[index].firstOption()?.requestFocusInWindow()
    }

    private fun advance() {
        setErrorText(null)
        if (sections[index].answer() == null) {
            setErrorText("Answer: " + sections[index].question.question)
            return
        }
        if (!isLast()) {
            showQuestion(index + 1)
            return
        }
        outcome = Outcome.ANSWER
        close(OK_EXIT_CODE)
    }

    private fun goBack() {
        if (index == 0) return
        setErrorText(null)
        showQuestion(index - 1)
    }

    private fun arrow(event: AnActionEvent, direction: Int) {
        val focused = IdeFocusManager.getInstance(null).focusOwner
        if (focused is JTextComponent) {
            focused.caretPosition = (focused.caretPosition + direction)
                .coerceIn(0, focused.text.length)
            return
        }
        if (direction < 0) goBack() else advance()
    }

    private fun selectByDigit(event: AnActionEvent) {
        val keyCode = (event.inputEvent as? KeyEvent)?.keyCode ?: return
        val focused = IdeFocusManager.getInstance(null).focusOwner
        if (focused is JTextComponent) {
            focused.replaceSelection((keyCode - KeyEvent.VK_0).toString())
            return
        }
        sections[index].selectAt(keyCode - KeyEvent.VK_1)
    }

    private fun isLast(): Boolean = index == sections.lastIndex

    private fun sendLabel(): String = Shortcuts.labelled("Send", Shortcuts.submitLabel())

    private fun nextLabel(): String = Shortcuts.labelled("Next", Shortcuts.submitLabel())

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
            otherToggle.addItemListener { event ->
                if (event.stateChange == ItemEvent.SELECTED) otherField.requestFocusInWindow()
            }
            Shortcuts.install(otherField, Shortcuts.arrow(KeyEvent.VK_UP)) {
                otherToggle.requestFocusInWindow()
            }
            add(indented(otherField))
            if (!question.multiSelect) {
                buttons.firstOrNull()?.first?.isSelected = true
            }
        }

        fun firstOption(): JComponent? = buttons.firstOrNull()?.first ?: otherToggle

        fun selectAt(index: Int) {
            val button = buttons.getOrNull(index)?.first ?: return
            button.doClick()
            button.requestFocusInWindow()
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
        const val MAX_HEIGHT = 520
        const val PADDING = 16
        const val GAP = 10
    }
}
