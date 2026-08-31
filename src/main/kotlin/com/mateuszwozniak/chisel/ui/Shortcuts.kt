package com.mateuszwozniak.chisel.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.util.SystemInfo
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.KeyStroke

object Shortcuts {

    fun submit(): ShortcutSet = withMenuKey(KeyEvent.VK_ENTER)

    fun revert(): ShortcutSet = withMenuKey(KeyEvent.VK_BACK_SPACE)

    fun reject(): ShortcutSet = withMenuKey(KeyEvent.VK_BACK_SPACE, shifted = true)

    fun comment(): ShortcutSet = withMenuKey(KeyEvent.VK_M, shifted = true)

    fun cycleModel(): ShortcutSet = withMenuKey(KeyEvent.VK_M, shifted = true)

    fun cycleEffort(): ShortcutSet = withMenuKey(KeyEvent.VK_E, shifted = true)

    const val ESCAPE_LABEL = "Esc"

    const val ENTER_LABEL = "Enter"

    const val DELETE_LABEL = "Del"

    fun delete(): ShortcutSet = CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0))

    fun enter(): ShortcutSet = CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0))

    fun interrupt(): ShortcutSet =
        CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK))

    const val INTERRUPT_LABEL = "Ctrl+C"

    fun labelled(text: String, shortcut: String): String = text + "  " + shortcut

    fun hinted(text: String, shortcut: String): String = text + " (" + shortcut + ")"

    fun submitLabel(): String = if (SystemInfo.isMac) "⌘⏎" else "Ctrl+Enter"

    fun revertLabel(): String = if (SystemInfo.isMac) "⌘⌫" else "Ctrl+Backspace"

    fun rejectLabel(): String = if (SystemInfo.isMac) "⌘⇧⌫" else "Ctrl+Shift+Backspace"

    fun commentLabel(): String = if (SystemInfo.isMac) "⌘⇧M" else "Ctrl+Shift+M"

    fun cycleModelLabel(): String = commentLabel()

    fun cycleEffortLabel(): String = if (SystemInfo.isMac) "⌘⇧E" else "Ctrl+Shift+E"

    fun cycleModeLabel(): String = if (SystemInfo.isMac) "⇧⇥" else "Shift+Tab"

    fun install(component: JComponent, shortcuts: ShortcutSet, run: (AnActionEvent) -> Unit) {
        val action = object : AnAction() {
            override fun actionPerformed(event: AnActionEvent) = run(event)
        }
        action.registerCustomShortcutSet(shortcuts, component)
    }

    fun arrow(keyCode: Int): ShortcutSet =
        CustomShortcutSet(KeyStroke.getKeyStroke(keyCode, 0))

    fun digits(): ShortcutSet = CustomShortcutSet(
        *(KeyEvent.VK_1..KeyEvent.VK_9)
            .map { KeyboardShortcut(KeyStroke.getKeyStroke(it, 0), null) }
            .toTypedArray()
    )

    private fun withMenuKey(keyCode: Int, shifted: Boolean = false): ShortcutSet {
        val menu = if (SystemInfo.isMac) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK
        val modifiers = if (shifted) menu or InputEvent.SHIFT_DOWN_MASK else menu
        return CustomShortcutSet(KeyStroke.getKeyStroke(keyCode, modifiers))
    }
}
