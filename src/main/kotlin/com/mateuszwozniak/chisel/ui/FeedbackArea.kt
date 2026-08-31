package com.mateuszwozniak.chisel.ui

import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.TextComponentEmptyText
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.util.function.Predicate
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent

class FeedbackArea(placeholder: String, rows: Int) : JPanel(BorderLayout()) {

    private val area = JBTextArea()

    private var preferredHeight = 0

    init {
        isOpaque = false
        border = JBUI.Borders.empty(8, 10)
        area.rows = rows
        area.lineWrap = true
        area.wrapStyleWord = true
        area.isOpaque = false
        area.font = JBUI.Fonts.label()
        area.border = JBUI.Borders.empty(2)
        area.emptyText.text = placeholder
        area.putClientProperty(
            TextComponentEmptyText.STATUS_VISIBLE_FUNCTION,
            Predicate<JBTextArea> { it.text.isEmpty() },
        )

        val scroll = ScrollPaneFactory.createScrollPane(area, true)
        scroll.isOpaque = false
        scroll.viewport.isOpaque = false
        add(scroll, BorderLayout.CENTER)
    }

    var text: String
        get() = area.text
        set(value) {
            area.text = value
        }

    val focusTarget: JComponent get() = area

    fun focus() {
        area.requestFocusInWindow()
    }

    fun resizeBy(delta: Int) {
        val current = if (preferredHeight > 0) preferredHeight else preferredSize.height
        preferredHeight = (current + delta).coerceIn(JBUI.scale(MIN_HEIGHT), JBUI.scale(MAX_HEIGHT))
        revalidate()
        repaint()
    }

    override fun getPreferredSize(): Dimension {
        val size = super.getPreferredSize()
        if (preferredHeight <= 0) return size
        return Dimension(size.width, preferredHeight)
    }

    fun onChanged(action: (String) -> Unit) {
        area.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) = action(area.text)
        })
    }

    override fun paintComponent(graphics: Graphics) {
        BadgeShape.paint(graphics, width, height, false)
    }

    private companion object {
        const val MIN_HEIGHT = 60
        const val MAX_HEIGHT = 400
    }
}
