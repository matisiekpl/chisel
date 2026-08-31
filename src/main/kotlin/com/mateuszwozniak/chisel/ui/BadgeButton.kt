package com.mateuszwozniak.chisel.ui

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.SwingConstants

class BadgeButton(text: String, icon: Icon?, onClick: () -> Unit) :
    JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(GAP), 0)) {

    init {
        isOpaque = false
        border = JBUI.Borders.empty(5, 10)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        val label = JBLabel(text, icon, SwingConstants.LEFT)
        label.font = JBUI.Fonts.smallFont()
        add(label)
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) = onClick()
        })
    }

    override fun paintComponent(graphics: Graphics) = BadgeShape.paint(graphics, width, height, false)

    private companion object {
        const val GAP = 6
    }
}
