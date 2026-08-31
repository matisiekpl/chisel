package com.mateuszwozniak.chisel.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel

class ResizeHandle(private val onResize: (Int) -> Unit) : JPanel() {

    private var anchor = 0

    init {
        isOpaque = false
        cursor = Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)
        preferredSize = Dimension(0, JBUI.scale(HANDLE_HEIGHT))
        val adapter = object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) {
                anchor = event.yOnScreen
            }

            override fun mouseDragged(event: MouseEvent) {
                onResize(anchor - event.yOnScreen)
                anchor = event.yOnScreen
            }
        }
        addMouseListener(adapter)
        addMouseMotionListener(adapter)
    }

    override fun paintComponent(graphics: Graphics) {
        val canvas = graphics.create() as Graphics2D
        canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        canvas.color = JBColor.border()
        val gripWidth = JBUI.scale(GRIP_WIDTH)
        val gripHeight = JBUI.scale(GRIP_HEIGHT)
        canvas.fillRoundRect(
            (width - gripWidth) / 2,
            (height - gripHeight) / 2,
            gripWidth,
            gripHeight,
            gripHeight,
            gripHeight,
        )
        canvas.dispose()
    }

    private companion object {
        const val HANDLE_HEIGHT = 8
        const val GRIP_WIDTH = 28
        const val GRIP_HEIGHT = 3
    }
}
