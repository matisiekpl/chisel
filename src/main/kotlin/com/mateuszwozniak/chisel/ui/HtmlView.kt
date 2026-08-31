package com.mateuszwozniak.chisel.ui

import com.intellij.lang.documentation.QuickDocHighlightingHelper
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBHtmlPane
import com.intellij.ui.components.JBHtmlPaneConfiguration
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.event.HyperlinkEvent

class HtmlView(parent: Disposable) {

    private val pane = JBHtmlPane(
        QuickDocHighlightingHelper.getDefaultDocStyleOptions(
            { EditorColorsManager.getInstance().globalScheme },
            false,
        ),
        JBHtmlPaneConfiguration(),
    )

    init {
        pane.isOpaque = false
        pane.isEditable = false
        Disposer.register(parent, pane)
    }

    val component: JComponent get() = pane

    fun setHtml(html: String) {
        pane.text = "<html><body>$html</body></html>"
    }

    fun onLinkClicked(action: (String) -> Unit) {
        pane.addHyperlinkListener { event ->
            if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) action(event.description)
        }
    }

    fun onClicked(action: () -> Unit) {
        pane.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        pane.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) = action()
        })
    }
}
