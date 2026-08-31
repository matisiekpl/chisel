package com.mateuszwozniak.chisel.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.mateuszwozniak.chisel.cli.ClaudeSessions
import com.mateuszwozniak.chisel.model.TerminalSession
import com.mateuszwozniak.chisel.service.ConversationManager

class ResumeSessionAction(
    private val project: Project,
) : AnAction("Resume Terminal Session", "Continue a session started with the Claude CLI", AllIcons.Vcs.History),
    DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        val sessions = ClaudeSessions.list(project.basePath)
        if (sessions.isEmpty()) {
            JBPopupFactory.getInstance()
                .createMessage("No Claude CLI sessions recorded for this project")
                .showInFocusCenter()
            return
        }
        JBPopupFactory.getInstance().createListPopup(SessionStep(sessions)).showInFocusCenter()
    }

    private inner class SessionStep(sessions: List<TerminalSession>) :
        BaseListPopupStep<TerminalSession>("Resume Terminal Session", sessions) {

        override fun getTextFor(value: TerminalSession): String = value.title

        override fun onChosen(value: TerminalSession, finalChoice: Boolean): PopupStep<*>? {
            val controller = ConversationManager.getInstance(project).create(value.id, value.title)
            ConversationTabs.open(project, controller)
            return FINAL_CHOICE
        }
    }
}
