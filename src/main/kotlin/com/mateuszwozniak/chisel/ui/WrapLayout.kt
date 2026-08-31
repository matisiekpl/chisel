package com.mateuszwozniak.chisel.ui

import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout

class WrapLayout(align: Int, horizontalGap: Int, verticalGap: Int) :
    FlowLayout(align, horizontalGap, verticalGap) {

    override fun preferredLayoutSize(target: Container): Dimension = layoutSize(target, true)

    override fun minimumLayoutSize(target: Container): Dimension = layoutSize(target, false)

    private fun layoutSize(target: Container, preferred: Boolean): Dimension {
        synchronized(target.treeLock) {
            val available = if (target.width > 0) target.width else Int.MAX_VALUE
            val insets = target.insets
            val maximum = available - (insets.left + insets.right + hgap * 2)
            val size = Dimension(0, 0)
            var rowWidth = 0
            var rowHeight = 0
            for (index in 0 until target.componentCount) {
                val component = target.getComponent(index)
                if (!component.isVisible) continue
                val componentSize = if (preferred) component.preferredSize else component.minimumSize
                if (rowWidth + componentSize.width > maximum && rowWidth > 0) {
                    addRow(size, rowWidth, rowHeight)
                    rowWidth = 0
                    rowHeight = 0
                }
                if (rowWidth != 0) rowWidth += hgap
                rowWidth += componentSize.width
                rowHeight = maxOf(rowHeight, componentSize.height)
            }
            addRow(size, rowWidth, rowHeight)
            size.width += insets.left + insets.right + hgap * 2
            size.height += insets.top + insets.bottom + vgap * 2
            return size
        }
    }

    private fun addRow(size: Dimension, rowWidth: Int, rowHeight: Int) {
        size.width = maxOf(size.width, rowWidth)
        if (size.height > 0) size.height += vgap
        size.height += rowHeight
    }
}
