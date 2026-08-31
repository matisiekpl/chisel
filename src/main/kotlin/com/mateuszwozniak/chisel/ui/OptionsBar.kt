package com.mateuszwozniak.chisel.ui

import com.intellij.util.ui.JBUI
import com.mateuszwozniak.chisel.model.AgentMode
import com.mateuszwozniak.chisel.model.AgentModel
import com.mateuszwozniak.chisel.model.Conversation
import com.mateuszwozniak.chisel.model.EffortLevel
import com.mateuszwozniak.chisel.service.ConversationController
import java.awt.FlowLayout
import javax.swing.JPanel

class OptionsBar(private val controller: ConversationController) :
    JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(GAP), 0)) {

    private val conversation: Conversation = controller.conversation

    private val mode = BadgeChoice(
        AgentMode.entries.toList(),
        conversation.mode,
        { it.label },
    ) { controller.changeMode(it) }

    private val model = BadgeChoice(
        AgentModel.entries.toList(),
        conversation.model,
        { it.label },
    ) { controller.changeModel(it) }

    private val effort = BadgeChoice(
        EffortLevel.entries.toList(),
        conversation.effort,
        { it.label },
    ) { controller.changeEffort(it) }

    init {
        isOpaque = false
        mode.toolTipText = Shortcuts.hinted("Mode", Shortcuts.cycleModeLabel())
        model.toolTipText = Shortcuts.hinted("Model", Shortcuts.cycleModelLabel())
        effort.toolTipText = Shortcuts.hinted("Effort", Shortcuts.cycleEffortLabel())
        add(mode)
        add(model)
        add(effort)
    }

    fun toggleMode() = mode.advance()

    fun cycleModel() = model.advance()

    fun cycleEffort() = effort.advance()

    fun refresh() {
        mode.show(conversation.mode)
        model.show(conversation.model)
        effort.show(conversation.effort)
    }

    private companion object {
        const val GAP = 6
    }
}
