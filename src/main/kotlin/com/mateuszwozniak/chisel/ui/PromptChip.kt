package com.mateuszwozniak.chisel.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.InplaceButton
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.SwingConstants

class PromptChip(
    text: String,
    icon: Icon?,
    tooltip: String?,
    onOpen: (() -> Unit)? = null,
    onRemove: () -> Unit,
) : JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(GAP), 0)) {

    init {
        isOpaque = false
        border = JBUI.Borders.empty(4, 8)
        toolTipText = tooltip
        val label = JBLabel(text, icon, SwingConstants.LEFT)
        label.font = JBUI.Fonts.smallFont()
        onOpen?.let { open ->
            label.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            label.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(event: MouseEvent) = open()
            })
        }
        add(label)
        val remove = InplaceButton("Remove", AllIcons.Actions.Close, ActionListener { onRemove() })
        remove.preferredSize = Dimension(JBUI.scale(REMOVE_SIZE), JBUI.scale(REMOVE_SIZE))
        add(remove)
    }

    override fun paintComponent(graphics: Graphics) = BadgeShape.paint(graphics, width, height, false)

    private companion object {
        const val GAP = 6
        const val REMOVE_SIZE = 16
    }
}
