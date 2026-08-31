package com.mateuszwozniak.chisel.ui.approval

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.editor.ComponentInlayAlignment
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.addComponentInlay
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.mateuszwozniak.chisel.ui.FeedbackArea
import com.mateuszwozniak.chisel.ui.Shortcuts
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.SwingUtilities

class AnnotationInlays(
    private val editor: EditorEx,
    private val model: AnnotationModel,
    private val parent: Disposable,
) {

    private val inlays = mutableMapOf<Int, Inlay<*>>()
    private val areas = mutableMapOf<Int, FeedbackArea>()
    private var hoveredLine = -1

    fun install() {
        val lineCount = editor.document.lineCount
        if (lineCount > MAX_LINES) return
        for (line in 0 until lineCount) {
            val start = editor.document.getLineStartOffset(line)
            val end = editor.document.getLineEndOffset(line)
            editor.markupModel.addRangeHighlighter(
                start,
                end,
                HighlighterLayer.LAST,
                null,
                HighlighterTargetArea.LINES_IN_RANGE,
            ).gutterIconRenderer = AddAnnotationRenderer(line)
        }
        editor.addEditorMouseMotionListener(HoverTracker(), parent)
        model.onCleared = { closeAll() }
        model.onCommentRequested = { openEditor(editor.caretModel.logicalPosition.line) }
        model.caretLine = editor.caretModel.logicalPosition.line
        editor.caretModel.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                model.caretLine = event.newPosition.line
            }
        }, parent)
        Shortcuts.install(editor.contentComponent, Shortcuts.comment()) {
            openEditor(editor.caretModel.logicalPosition.line)
        }
    }

    private fun leaveEditor(line: Int) {
        if (model.textAt(line) == null) closeEditor(line) else editor.contentComponent.requestFocusInWindow()
    }

    private fun closeAll() {
        inlays.values.forEach(Disposer::dispose)
        inlays.clear()
        areas.clear()
    }

    private fun closeEditor(line: Int) {
        inlays.remove(line)?.let(Disposer::dispose)
        areas.remove(line)
        model.remove(line)
        editor.contentComponent.requestFocusInWindow()
    }

    private fun openEditor(line: Int) {
        areas[line]?.let {
            it.focus()
            return
        }
        val area = FeedbackArea("Comment on this line", COMMENT_ROWS)
        area.text = model.textAt(line).orEmpty()
        area.onChanged { text -> model.put(line, text) }
        Shortcuts.install(area, CommonShortcuts.ESCAPE) { leaveEditor(line) }
        Shortcuts.install(area, Shortcuts.enter()) { leaveEditor(line) }
        Shortcuts.install(area, Shortcuts.delete()) { closeEditor(line) }
        val panel = buildEditorPanel(line, area)
        val offset = editor.document.getLineEndOffset(line)
        val properties = InlayProperties().priority(0).relatesToPrecedingText(true)
        val inlay = editor.addComponentInlay(
            offset,
            properties,
            panel,
            ComponentInlayAlignment.FIT_VIEWPORT_WIDTH,
        ) ?: return
        inlays[line] = inlay
        areas[line] = area
        Disposer.register(parent, inlay)
        SwingUtilities.invokeLater { area.focus() }
    }

    private fun buildEditorPanel(line: Int, area: FeedbackArea): JPanel {
        val actions = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0))
        actions.isOpaque = false
        actions.isVisible = false
        actions.add(ActionLink(Shortcuts.hinted("Save comment", Shortcuts.ENTER_LABEL)) { leaveEditor(line) })
        actions.add(ActionLink(Shortcuts.hinted("Remove comment", Shortcuts.DELETE_LABEL)) { closeEditor(line) })

        val panel = JPanel(BorderLayout())
        panel.isOpaque = false
        panel.border = JBUI.Borders.empty(2, 24, 6, 8)
        panel.add(area, BorderLayout.CENTER)
        panel.add(actions, BorderLayout.SOUTH)

        area.focusTarget.addFocusListener(object : FocusAdapter() {
            override fun focusGained(event: FocusEvent) = showActions(actions, true)

            override fun focusLost(event: FocusEvent) {
                val opposite = event.oppositeComponent
                if (opposite != null && SwingUtilities.isDescendingFrom(opposite, panel)) return
                showActions(actions, false)
            }
        })
        return panel
    }

    private fun showActions(actions: JPanel, visible: Boolean) {
        actions.isVisible = visible
        actions.revalidate()
        actions.repaint()
    }

    private inner class HoverTracker : EditorMouseMotionListener {
        override fun mouseMoved(event: EditorMouseEvent) {
            val line = event.logicalPosition.line
            if (line == hoveredLine) return
            hoveredLine = line
            editor.gutterComponentEx.repaint()
        }
    }

    private inner class AddAnnotationRenderer(private val line: Int) : GutterIconRenderer(), DumbAware {

        override fun getIcon(): Icon =
            if (line == hoveredLine || model.textAt(line) != null) AllIcons.General.Add
            else EmptyIcon.ICON_16

        override fun getAlignment(): Alignment = Alignment.RIGHT

        override fun getTooltipText(): String = "Comment on this line (" + Shortcuts.commentLabel() + ")"

        override fun isNavigateAction(): Boolean = true

        override fun getClickAction(): AnAction = object : AnAction() {
            override fun actionPerformed(event: AnActionEvent) = openEditor(line)
        }

        override fun equals(other: Any?): Boolean =
            other is AddAnnotationRenderer && other.line == line

        override fun hashCode(): Int = line
    }

    private companion object {
        const val MAX_LINES = 3000
        const val COMMENT_ROWS = 2
    }
}
