package com.mateuszwozniak.chisel.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.mateuszwozniak.chisel.cli.ClaudeAccount

class AccountAction(
    private val project: Project,
) : AnAction("Account", "Show the signed-in Claude account and usage", AllIcons.General.User), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        AccountDialog(project, ClaudeAccount.read()).show()
    }
}
