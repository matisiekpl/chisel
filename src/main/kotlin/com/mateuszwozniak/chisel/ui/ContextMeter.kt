package com.mateuszwozniak.chisel.ui

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.mateuszwozniak.chisel.model.ContextUsage
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JPanel

class ContextMeter : JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(GAP), 0)) {

    private val label = JBLabel().apply {
        font = JBUI.Fonts.smallFont()
        foreground = UIUtil.getContextHelpForeground()
    }

    private val bar = Bar()

    init {
        isOpaque = false
        isVisible = false
        add(label)
        add(bar)
    }

    fun show(usage: ContextUsage?) {
        if (usage == null) {
            isVisible = false
            return
        }
        label.text = usage.percentage.toString() + "%"
        bar.fill = usage.percentage.coerceIn(0, 100) / 100f
        val hint = tooltip(usage)
        toolTipText = hint
        label.toolTipText = hint
        bar.toolTipText = hint
        isVisible = true
        revalidate()
        repaint()
    }

    private fun tooltip(usage: ContextUsage): String {
        val rows = usage.categories
            .filter { it.tokens > 0 }
            .joinToString("<br>") { it.name + ": " + tokens(it.tokens) }
        return "<html>Context " + tokens(usage.usedTokens) + " of " + tokens(usage.maxTokens) +
            "<br>The conversation is compacted when this fills up.<br><br>" + rows + "</html>"
    }

    private fun tokens(value: Long): String =
        if (value < 1000) value.toString() else (value / 1000).toString() + "k"

    private class Bar : JPanel() {

        var fill: Float = 0f

        init {
            isOpaque = false
            preferredSize = Dimension(JBUI.scale(WIDTH), JBUI.scale(HEIGHT))
        }

        override fun paintComponent(graphics: Graphics) {
            val canvas = graphics.create() as Graphics2D
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val arc = height
            canvas.color = JBColor.border()
            canvas.fillRoundRect(0, 0, width, height, arc, arc)
            val filled = (width * fill).toInt().coerceIn(0, width)
            if (filled > 0) {
                canvas.color = if (fill >= WARNING_LEVEL) WARNING else JBUI.CurrentTheme.Focus.focusColor()
                canvas.fillRoundRect(0, 0, filled.coerceAtLeast(arc), height, arc, arc)
            }
            canvas.dispose()
        }

        private companion object {
            const val WIDTH = 44
            const val HEIGHT = 6
            const val WARNING_LEVEL = 0.8f
            val WARNING = JBColor(Color(0xC7, 0x7D, 0x1A), Color(0xD9, 0xA3, 0x3C))
        }
    }

    private companion object {
        const val GAP = 5
    }
}
