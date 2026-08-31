package com.mateuszwozniak.chisel.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.InplaceButton
import com.intellij.ui.JBColor
import com.intellij.util.textCompletion.TextFieldWithCompletion
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ActionListener
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke

class PromptInput(
    private val project: Project,
    private val modeToggle: ModeToggle,
    private val onSubmit: (String) -> Unit,
    private val onStop: () -> Unit,
) : JPanel(BorderLayout()) {

    private val promptField = TextFieldWithCompletion(
        project,
        FilePathCompletionProvider(project),
        "",
        false,
        true,
        false,
    )

    private val sendButton = InplaceButton(
        "Send (" + submitShortcutLabel() + ")",
        AllIcons.Actions.Execute,
        ActionListener { submit() },
    )

    private val stopButton = InplaceButton(
        "Stop (Escape)",
        AllIcons.Actions.Pause,
        ActionListener { onStop() },
    )

    private val card = Card()

    private var busy = false

    init {
        border = JBUI.Borders.empty(6, 8, 8, 8)
        promptField.setPlaceholder("Ask a question, or type @ to reference a file")
        promptField.border = JBUI.Borders.empty()
        promptField.background = UIUtil.getTextFieldBackground()
        promptField.preferredSize = Dimension(0, JBUI.scale(FIELD_HEIGHT))
        promptField.addSettingsProvider { editor ->
            editor.setBorder(JBUI.Borders.empty())
            editor.settings.isUseSoftWraps = true
        }
        sendButton.preferredSize = Dimension(JBUI.scale(BUTTON_SIZE), JBUI.scale(BUTTON_SIZE))
        stopButton.preferredSize = Dimension(JBUI.scale(BUTTON_SIZE), JBUI.scale(BUTTON_SIZE))
        promptField.addFocusListener(object : FocusAdapter() {
            override fun focusGained(event: FocusEvent) = card.showFocused(true)

            override fun focusLost(event: FocusEvent) = card.showFocused(false)
        })

        card.add(promptField, BorderLayout.CENTER)
        card.add(buildActions(), BorderLayout.SOUTH)
        add(card, BorderLayout.CENTER)

        installShortcuts()
        showBusy(false)
    }

    var text: String
        get() = promptField.text
        set(value) {
            promptField.text = value
        }

    fun showBusy(busy: Boolean) {
        this.busy = busy
        sendButton.isVisible = !busy
        stopButton.isVisible = busy
    }

    fun requestFocusOnField() {
        promptField.requestFocusInWindow()
    }

    private fun buildActions(): JComponent {
        val row = JPanel(BorderLayout())
        row.isOpaque = false
        row.border = JBUI.Borders.emptyTop(6)

        val left = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        left.isOpaque = false
        left.add(modeToggle)

        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0))
        right.isOpaque = false
        right.add(sendButton)
        right.add(stopButton)

        row.add(left, BorderLayout.WEST)
        row.add(right, BorderLayout.EAST)
        return row
    }

    private fun installShortcuts() {
        val submitAction = object : AnAction() {
            override fun actionPerformed(event: AnActionEvent) = submit()
        }
        submitAction.registerCustomShortcutSet(submitShortcut(), promptField)

        val stopAction = object : AnAction() {
            override fun actionPerformed(event: AnActionEvent) {
                if (busy) onStop()
            }
        }
        stopAction.registerCustomShortcutSet(CommonShortcuts.ESCAPE, promptField)

        val modeAction = object : AnAction() {
            override fun actionPerformed(event: AnActionEvent) = modeToggle.toggle()
        }
        modeAction.registerCustomShortcutSet(modeShortcut(), promptField)
    }

    private fun submitShortcut(): ShortcutSet = CustomShortcutSet(
        KeyStroke.getKeyStroke(
            KeyEvent.VK_ENTER,
            if (SystemInfo.isMac) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK,
        ),
    )

    private fun modeShortcut(): ShortcutSet = CustomShortcutSet(
        KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK),
    )

    private fun submitShortcutLabel(): String = if (SystemInfo.isMac) "⌘⏎" else "Ctrl+Enter"

    private fun submit() {
        if (busy) return
        val value = promptField.text.trim()
        if (value.isEmpty()) return
        promptField.text = ""
        onSubmit(value)
    }

    private class Card : JPanel(BorderLayout()) {

        private var focused = false

        init {
            isOpaque = false
            border = JBUI.Borders.empty(8, 10)
        }

        fun showFocused(value: Boolean) {
            focused = value
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            val graphics = g.create() as Graphics2D
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val arc = JBUI.scale(ARC).toFloat()
            val shape = RoundRectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f, arc, arc)
            graphics.color = UIUtil.getTextFieldBackground()
            graphics.fill(shape)
            graphics.color = if (focused) JBUI.CurrentTheme.Focus.focusColor() else JBColor.border()
            graphics.stroke = BasicStroke(if (focused) 2f else 1f)
            graphics.draw(shape)
            graphics.dispose()
        }
    }

    private companion object {
        const val FIELD_HEIGHT = 88
        const val ARC = 14
        const val BUTTON_SIZE = 24
    }
}
