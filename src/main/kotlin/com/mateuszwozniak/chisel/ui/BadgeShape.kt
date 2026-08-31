package com.mateuszwozniak.chisel.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BasicStroke
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints

object BadgeShape {

    fun paint(graphics: Graphics, width: Int, height: Int, dashed: Boolean) {
        val canvas = graphics.create() as Graphics2D
        canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val arc = JBUI.scale(ARC)
        canvas.color = UIUtil.getTextFieldBackground()
        canvas.fillRoundRect(0, 0, width, height, arc, arc)
        canvas.color = JBColor.border()
        if (dashed) {
            val dash = JBUI.scale(DASH).toFloat()
            canvas.stroke = BasicStroke(
                1f,
                BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_ROUND,
                1f,
                floatArrayOf(dash, dash),
                0f,
            )
        }
        canvas.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
        canvas.dispose()
    }

    private const val ARC = 10

    private const val DASH = 3
}
