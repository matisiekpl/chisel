package com.mateuszwozniak.chisel.ui

import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JPanel
import javax.swing.Scrollable

class VerticalList(gap: Int) : JPanel(VerticalLayout(JBUI.scale(gap))), Scrollable {

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

    override fun getScrollableUnitIncrement(visible: Rectangle, orientation: Int, direction: Int): Int =
        JBUI.scale(UNIT_INCREMENT)

    override fun getScrollableBlockIncrement(visible: Rectangle, orientation: Int, direction: Int): Int =
        visible.height

    override fun getScrollableTracksViewportWidth(): Boolean = true

    override fun getScrollableTracksViewportHeight(): Boolean = false

    private companion object {
        const val UNIT_INCREMENT = 16
    }
}
