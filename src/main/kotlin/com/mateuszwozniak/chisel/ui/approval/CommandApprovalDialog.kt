package com.mateuszwozniak.chisel.ui.approval

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapper.IdeModalityType
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.mateuszwozniak.chisel.ui.BadgeShape
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

class CommandApprovalDialog(
    project: Project,
    private val toolName: String,
    private val command: String,
    private val subtitle: String?,
) : DialogWrapper(project, true, IdeModalityType.MODELESS) {

    enum class Outcome { ALLOW, DENY }

    private val allowAction = object : DialogWrapperAction("Allow") {
        override fun doAction(event: ActionEvent) {
            outcome = Outcome.ALLOW
            close(OK_EXIT_CODE)
        }
    }

    private val denyAction = object : DialogWrapperAction("Deny") {
        override fun doAction(event: ActionEvent) = doCancelAction()
    }

    var outcome: Outcome = Outcome.DENY
        private set

    init {
        title = "Allow $toolName?"
        allowAction.putValue(DEFAULT_ACTION, true)
        allowAction.putValue(Action.SHORT_DESCRIPTION, "Enter")
        denyAction.putValue(Action.SHORT_DESCRIPTION, "Escape")
        init()
        colorButtons()
    }

    override fun doCancelAction() {
        outcome = Outcome.DENY
        super.doCancelAction()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, JBUI.scale(8)))
        panel.preferredSize = Dimension(JBUI.scale(WIDTH), JBUI.scale(HEIGHT))
        subtitle?.takeIf { it.isNotBlank() }?.let { text ->
            panel.add(subtitleLabel(text), BorderLayout.NORTH)
        }
        panel.add(commandCard(), BorderLayout.CENTER)
        return panel
    }

    override fun createActions(): Array<Action> = arrayOf(denyAction, allowAction)

    private fun subtitleLabel(text: String): JComponent =
        JBLabel("<html>$text</html>").apply { foreground = UIUtil.getContextHelpForeground() }

    private fun commandCard(): JComponent {
        val area = JBTextArea(command)
        area.isEditable = false
        area.isOpaque = false
        area.lineWrap = true
        area.border = JBUI.Borders.empty(2)
        area.font = EditorColorsManager.getInstance().globalScheme.getFont(EditorFontType.PLAIN)

        val scroll = ScrollPaneFactory.createScrollPane(area, true)
        scroll.isOpaque = false
        scroll.viewport.isOpaque = false
        scroll.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER

        val card = object : JPanel(BorderLayout()) {
            override fun paintComponent(graphics: Graphics) =
                BadgeShape.paint(graphics, width, height, false)
        }
        card.isOpaque = false
        card.border = JBUI.Borders.empty(10, 12)
        card.add(scroll, BorderLayout.CENTER)
        return card
    }

    private fun colorButtons() {
        paint(getButton(allowAction), ALLOW_BACKGROUND)
        paint(getButton(denyAction), DENY_BACKGROUND)
    }

    private fun paint(button: JComponent?, background: Color) {
        button?.putClientProperty("JButton.backgroundColor", background)
        button?.putClientProperty("JButton.textColor", BUTTON_TEXT)
    }

    private companion object {
        const val WIDTH = 620
        const val HEIGHT = 180
        val ALLOW_BACKGROUND = JBColor(Color(0x2E, 0x7D, 0x32), Color(0x37, 0x6E, 0x3F))
        val DENY_BACKGROUND = JBColor(Color(0xB3, 0x3A, 0x30), Color(0x9B, 0x36, 0x2E))
        val BUTTON_TEXT = JBColor(Color(0xFF, 0xFF, 0xFF), Color(0xEC, 0xEC, 0xEC))
    }
}
