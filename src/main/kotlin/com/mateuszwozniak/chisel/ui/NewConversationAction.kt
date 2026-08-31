package com.mateuszwozniak.chisel.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.mateuszwozniak.chisel.service.ConversationManager

class NewConversationAction(
    private val project: Project,
) : AnAction("New Conversation", "Start a new conversation", AllIcons.General.Add), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        ConversationTabs.open(project, ConversationManager.getInstance(project).create())
    }
}
