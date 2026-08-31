package com.mateuszwozniak.chisel.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.popup.PopupState
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel

class BadgeChoice<T : Any>(
    private val items: List<T>,
    initial: T,
    private val labelOf: (T) -> String,
    private val onChanged: (T) -> Unit,
) : JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(GAP), 0)) {

    private val label = JBLabel()

    private val popupState = PopupState.forPopup()

    private var selected = initial

    init {
        isOpaque = false
        border = JBUI.Borders.empty(5, 8)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        label.font = JBUI.Fonts.smallFont()
        add(label)
        add(JBLabel(AllIcons.General.ChevronDown))
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) = openPopup()
        })
        show(initial)
    }

    override fun paintComponent(graphics: Graphics) {
        BadgeShape.paint(graphics, width, height, false)
    }

    fun show(value: T) {
        selected = value
        label.text = labelOf(value)
        revalidate()
        repaint()
    }

    fun advance() {
        val next = (items.indexOf(selected) + 1) % items.size
        select(items[next])
    }

    private fun select(value: T) {
        if (value == selected) return
        show(value)
        onChanged(value)
    }

    private fun openPopup() {
        if (popupState.isRecentlyHidden) return
        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(items)
            .setRenderer(SimpleListCellRenderer.create("") { item: T -> labelOf(item) })
            .setSelectedValue(selected, true)
            .setItemChosenCallback { chosen -> select(chosen) }
            .createPopup()
        popupState.prepareToShow(popup)
        popup.showUnderneathOf(this)
    }

    private companion object {
        const val GAP = 4
    }
}
