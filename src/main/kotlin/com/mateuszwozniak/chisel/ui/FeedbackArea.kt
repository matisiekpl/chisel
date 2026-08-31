package com.mateuszwozniak.chisel.ui

import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Graphics
import javax.swing.JPanel
import javax.swing.event.DocumentEvent

class FeedbackArea(placeholder: String, rows: Int) : JPanel(BorderLayout()) {

    private val area = JBTextArea()

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

    fun focus() {
        area.requestFocusInWindow()
    }

    fun onChanged(action: (String) -> Unit) {
        area.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) = action(area.text)
        })
    }

    override fun paintComponent(graphics: Graphics) {
        BadgeShape.paint(graphics, width, height, false)
    }
}
