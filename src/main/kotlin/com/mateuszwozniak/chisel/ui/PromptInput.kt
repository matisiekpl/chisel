package com.mateuszwozniak.chisel.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.ui.InplaceButton
import com.intellij.ui.JBColor
import com.intellij.util.textCompletion.TextFieldWithCompletion
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.mateuszwozniak.chisel.model.PromptAttachment
import com.mateuszwozniak.chisel.model.QueuedPrompt
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.event.ActionListener
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import java.nio.file.Paths
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke

class PromptInput(
    private val project: Project,
    private val options: OptionsBar,
    private val onSubmit: (String, List<PromptAttachment>) -> Unit,
    private val onStop: () -> Unit,
) : JPanel(BorderLayout()) {

    private var fieldHeight = JBUI.scale(DEFAULT_HEIGHT)

    private val promptField = object : TextFieldWithCompletion(
        project,
        FilePathCompletionProvider(project) { commands() },
        "",
        false,
        true,
        false,
    ) {
        override fun getPreferredSize(): Dimension = Dimension(0, fieldHeight)
    }

    private val sendButton = InplaceButton(
        "Send (" + Shortcuts.submitLabel() + ")",
        AllIcons.Actions.Execute,
        ActionListener { submit() },
    )

    private val stopButton = InplaceButton(
        "Stop (Escape or " + Shortcuts.INTERRUPT_LABEL + ")",
        AllIcons.Actions.Pause,
        ActionListener { onStop() },
    )

    private val pasteAction = object : AnAction() {
        override fun actionPerformed(event: AnActionEvent) = paste(event)
    }

    private val editQueueAction = object : AnAction() {
        override fun actionPerformed(event: AnActionEvent) = recallPrevious()
    }

    private val historyForwardAction = object : AnAction() {
        override fun actionPerformed(event: AnActionEvent) = recallNext()
    }

    private val attachButton = InplaceButton(
        "Attach files",
        AllIcons.Actions.Attach,
        ActionListener { chooseFiles() },
    )

    private val attachments = AttachmentBar(project)

    private val queue = QueueBar { prompt -> onQueueRemove(prompt) }

    private val card = Card()

    private val resizeHandle = ResizeHandle { resizeBy(it) }

    var onQueueRemove: (QueuedPrompt) -> Unit = {}

    var onQueuePop: () -> QueuedPrompt? = { null }

    var onHistory: (Int) -> String? = { null }

    var commands: () -> List<String> = { emptyList() }

    private val dropListener = object : DropTargetAdapter() {
        override fun dragOver(event: DropTargetDragEvent) {
            if (AttachmentTransfer.holdsFiles(event.transferable)) event.acceptDrag(DnDConstants.ACTION_COPY)
            else event.rejectDrag()
        }

        override fun drop(event: DropTargetDropEvent) {
            event.acceptDrop(DnDConstants.ACTION_COPY)
            val images = AttachmentTransfer.read(event.transferable)
            images.forEach(attachments::attach)
            event.dropComplete(images.isNotEmpty())
        }
    }

    private var busy = false

    private var historyIndex = -1

    private var recalled: String? = null

    init {
        border = JBUI.Borders.empty(6, 8, 8, 8)
        promptField.setPlaceholder("Ask a question, or type @ to reference a file")
        promptField.border = JBUI.Borders.empty(0, TEXT_INSET)
        promptField.background = UIUtil.getTextFieldBackground()
        promptField.addSettingsProvider { editor ->
            editor.setBorder(JBUI.Borders.empty())
            editor.settings.isUseSoftWraps = true
            editor.setVerticalScrollbarVisible(true)
            editor.scrollPane.verticalScrollBar.isOpaque = false
            editor.setShowPlaceholderWhenFocused(true)
            editor.contentComponent.dropTarget =
                DropTarget(editor.contentComponent, DnDConstants.ACTION_COPY, dropListener, true)
            pasteShortcut()?.let { pasteAction.registerCustomShortcutSet(it, editor.contentComponent) }
            editQueueAction.registerCustomShortcutSet(Shortcuts.arrow(KeyEvent.VK_UP), editor.contentComponent)
            historyForwardAction.registerCustomShortcutSet(
                Shortcuts.arrow(KeyEvent.VK_DOWN),
                editor.contentComponent,
            )
        }
        sendButton.preferredSize = Dimension(JBUI.scale(BUTTON_SIZE), JBUI.scale(BUTTON_SIZE))
        stopButton.preferredSize = Dimension(JBUI.scale(BUTTON_SIZE), JBUI.scale(BUTTON_SIZE))
        attachButton.preferredSize = Dimension(JBUI.scale(BUTTON_SIZE), JBUI.scale(BUTTON_SIZE))
        promptField.addFocusListener(object : FocusAdapter() {
            override fun focusGained(event: FocusEvent) = card.showFocused(true)

            override fun focusLost(event: FocusEvent) = card.showFocused(false)
        })

        card.add(attachments, BorderLayout.NORTH)
        card.add(promptField, BorderLayout.CENTER)
        card.add(buildActions(), BorderLayout.SOUTH)

        val header = JPanel(BorderLayout())
        header.isOpaque = false
        header.add(queue, BorderLayout.NORTH)
        header.add(resizeHandle, BorderLayout.SOUTH)
        add(header, BorderLayout.NORTH)
        add(card, BorderLayout.CENTER)

        installShortcuts()
        installDropTarget()
        showBusy(false)
    }

    var text: String
        get() = promptField.text
        set(value) {
            promptField.text = value
        }

    fun showBusy(busy: Boolean) {
        this.busy = busy
        stopButton.isVisible = busy
        sendButton.isVisible = !busy
    }

    val focusTarget: JComponent get() = promptField

    fun requestFocusOnField() {
        promptField.requestFocusInWindow()
    }

    fun showQueue(queued: List<QueuedPrompt>) {
        queue.show(queued)
        revalidate()
        repaint()
    }

    private fun resizeBy(delta: Int) {
        val next = (fieldHeight + delta).coerceIn(JBUI.scale(MIN_HEIGHT), JBUI.scale(MAX_HEIGHT))
        if (next == fieldHeight) return
        fieldHeight = next
        revalidate()
        repaint()
    }

    private fun buildActions(): JComponent {
        val row = JPanel(BorderLayout())
        row.isOpaque = false
        row.border = JBUI.Borders.emptyTop(6)

        val left = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        left.isOpaque = false
        left.add(options)

        val right = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), 0))
        right.isOpaque = false
        right.border = JBUI.Borders.emptyRight(TEXT_INSET)
        right.add(attachButton)
        right.add(stopButton)
        right.add(sendButton)

        row.add(left, BorderLayout.WEST)
        row.add(right, BorderLayout.EAST)
        return row
    }

    private fun installShortcuts() {
        val submitAction = object : AnAction() {
            override fun actionPerformed(event: AnActionEvent) = submit()
        }
        submitAction.registerCustomShortcutSet(Shortcuts.submit(), promptField)

        val stopAction = object : AnAction() {
            override fun actionPerformed(event: AnActionEvent) {
                if (busy) onStop()
            }
        }
        stopAction.registerCustomShortcutSet(CommonShortcuts.ESCAPE, promptField)

        val interruptAction = object : AnAction() {
            override fun actionPerformed(event: AnActionEvent) = interruptOrCopy()
        }
        interruptAction.registerCustomShortcutSet(Shortcuts.interrupt(), promptField)

        val modeAction = object : AnAction() {
            override fun actionPerformed(event: AnActionEvent) = options.toggleMode()
        }
        modeAction.registerCustomShortcutSet(modeShortcut(), promptField)

        val modelAction = object : AnAction() {
            override fun actionPerformed(event: AnActionEvent) = options.cycleModel()
        }
        modelAction.registerCustomShortcutSet(Shortcuts.cycleModel(), promptField)

        val effortAction = object : AnAction() {
            override fun actionPerformed(event: AnActionEvent) = options.cycleEffort()
        }
        effortAction.registerCustomShortcutSet(Shortcuts.cycleEffort(), promptField)

        pasteShortcut()?.let { pasteAction.registerCustomShortcutSet(it, promptField) }
        editQueueAction.registerCustomShortcutSet(Shortcuts.arrow(KeyEvent.VK_UP), promptField)
        historyForwardAction.registerCustomShortcutSet(Shortcuts.arrow(KeyEvent.VK_DOWN), promptField)
    }

    private fun pasteShortcut(): ShortcutSet? =
        ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_PASTE)?.shortcutSet

    private fun recallPrevious() {
        if (caretLine() > 0) {
            moveCaret(-1)
            return
        }
        val queued = onQueuePop()
        if (queued != null) {
            promptField.text = queued.text
            attachments.attachAll(queued.attachments)
            historyIndex = -1
            recalled = null
            return
        }
        if (recall(1) && busy) onStop()
    }

    private fun recallNext() {
        if (recalled == null || caretLine() < lastLine()) {
            moveCaret(1)
            return
        }
        recall(-1)
    }

    private fun moveCaret(delta: Int) {
        val editor = promptField.editor ?: return
        val position = editor.caretModel.logicalPosition
        val line = (position.line + delta).coerceIn(0, editor.document.lineCount - 1)
        editor.caretModel.moveToLogicalPosition(LogicalPosition(line, position.column))
    }

    private fun recall(step: Int): Boolean {
        if (promptField.text != recalled) historyIndex = -1
        val next = historyIndex + step
        if (next < 0) {
            historyIndex = -1
            recalled = null
            promptField.text = ""
            return true
        }
        val text = onHistory(next) ?: return false
        historyIndex = next
        recalled = text
        promptField.text = text
        return true
    }

    private fun caretLine(): Int = promptField.editor?.caretModel?.logicalPosition?.line ?: 0

    private fun lastLine(): Int = promptField.editor?.document?.let { it.lineCount - 1 } ?: 0

    private fun paste(event: AnActionEvent) {
        val transferable = CopyPasteManager.getInstance().contents
        val files = AttachmentTransfer.read(transferable)
        if (files.isNotEmpty()) {
            files.forEach(attachments::attach)
            return
        }
        val text = runCatching {
            transferable?.getTransferData(DataFlavor.stringFlavor) as? String
        }.getOrNull() ?: return
        insert(text)
    }

    private fun interruptOrCopy() {
        val editor = promptField.editor
        val selected = editor?.selectionModel?.selectedText
        if (!selected.isNullOrEmpty()) {
            CopyPasteManager.getInstance().setContents(StringSelection(selected))
            return
        }
        if (busy) onStop()
    }

    private fun insert(text: String) {
        val editor = promptField.editor ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            val selection = editor.selectionModel
            val start = if (selection.hasSelection()) selection.selectionStart else editor.caretModel.offset
            val end = if (selection.hasSelection()) selection.selectionEnd else start
            editor.document.replaceString(start, end, text)
            editor.caretModel.moveToOffset(start + text.length)
            selection.removeSelection()
        }
    }

    private fun installDropTarget() {
        DropTarget(this, DnDConstants.ACTION_COPY, dropListener, true)
        DropTarget(card, DnDConstants.ACTION_COPY, dropListener, true)
    }

    private fun chooseFiles() {
        val descriptor = FileChooserDescriptorFactory.createMultipleFilesNoJarsDescriptor()
        FileChooser.chooseFiles(descriptor, project, null).forEach { file ->
            PromptAttachment.of(Paths.get(file.path))?.let(attachments::attach)
        }
    }

    private fun modeShortcut(): ShortcutSet = CustomShortcutSet(
        KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK),
    )

    private fun submit() {
        val typed = promptField.text.trim()
        val images = attachments.all()
        val value = if (typed.isEmpty() && images.isEmpty()) CONTINUE else typed
        promptField.text = ""
        attachments.clear()
        historyIndex = -1
        recalled = null
        onSubmit(value, images)
    }

    private class Card : JPanel(BorderLayout()) {

        private var focused = false

        init {
            isOpaque = false
            border = JBUI.Borders.empty(8, EDGE)
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
        const val DEFAULT_HEIGHT = 88
        const val MIN_HEIGHT = 48
        const val MAX_HEIGHT = 600
        const val ARC = 14
        const val BUTTON_SIZE = 24
        const val CONTINUE = "Continue"
        const val EDGE = 4
        const val TEXT_INSET = 6
    }
}
