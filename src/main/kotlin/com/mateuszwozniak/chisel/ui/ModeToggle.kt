package com.mateuszwozniak.chisel.ui

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleListCellRenderer
import com.mateuszwozniak.chisel.model.AgentMode
import java.awt.event.ItemEvent

class ModeToggle(initial: AgentMode, private val onChanged: (AgentMode) -> Unit) :
    ComboBox<AgentMode>(AgentMode.entries.toTypedArray()) {

    private var applying = false

    init {
        isOpaque = false
        putClientProperty("JComboBox.isBorderless", true)
        renderer = SimpleListCellRenderer.create("") { mode: AgentMode -> mode.label }
        selectedItem = initial
        addItemListener { event ->
            if (applying || event.stateChange != ItemEvent.SELECTED) return@addItemListener
            (event.item as? AgentMode)?.let(onChanged)
        }
    }

    fun toggle() {
        val current = selectedItem as? AgentMode ?: return
        selectedItem = if (current == AgentMode.PLAN) AgentMode.IMPLEMENTATION else AgentMode.PLAN
    }

    fun show(mode: AgentMode) {
        applying = true
        selectedItem = mode
        applying = false
    }
}
