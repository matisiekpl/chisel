package com.mateuszwozniak.chisel.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.mateuszwozniak.chisel.service.ConversationController
import com.mateuszwozniak.chisel.service.ConversationManager
import com.mateuszwozniak.chisel.service.TranscriptExport
import java.awt.BorderLayout
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

class ConversationToolbar(
    private val project: Project,
    private val controller: ConversationController,
    private val onAgents: () -> Unit,
) {

    private var remoteDialog: RemoteControlDialog? = null

    private val usageLabel = JBLabel().apply {
        font = JBUI.Fonts.smallFont()
        foreground = UIUtil.getContextHelpForeground()
        border = JBUI.Borders.empty(0, 8)
        isVisible = false
    }

    fun showUsage() {
        val conversation = controller.conversation
        if (conversation.inputTokens == 0L && conversation.outputTokens == 0L) {
            usageLabel.isVisible = false
            return
        }
        usageLabel.text = "\u2193 " + tokens(conversation.inputTokens) +
            "   \u2191 " + tokens(conversation.outputTokens) +
            "   " + cost(conversation.costUsd)
        usageLabel.toolTipText = "Tokens sent and received in this conversation, and its total cost"
        usageLabel.isVisible = true
    }

    private fun tokens(value: Long): String =
        if (value < 1000) value.toString() else (value / 1000).toString() + "k"

    private fun cost(value: Double): String = "$" + String.format("%.4f", value)

    fun component(): JComponent {
        val actions = DefaultActionGroup(
            action("Continue", "Ask the agent to keep going", AllIcons.Actions.Resume) {
                controller.sendPrompt(CONTINUE)
            },
            action("Compact", "Summarise earlier turns to free context", AllIcons.Actions.Collapseall) {
                compact()
            },
            action("Subagents", "Show agents delegated in this conversation", AllIcons.General.Groups) {
                onAgents()
            },
            remoteAction(),
            Separator.getInstance(),
            action("Export", "Save the transcript as Markdown", AllIcons.General.Export) { export() },
            action("Delete", "Delete this conversation", AllIcons.Actions.GC) { delete() },
        )
        val toolbar = ActionManager.getInstance().createActionToolbar(PLACE, actions, true)
        toolbar.targetComponent = toolbar.component
        val row = JPanel(BorderLayout())
        row.isOpaque = false
        row.add(toolbar.component, BorderLayout.WEST)
        row.add(usageLabel, BorderLayout.EAST)
        return row
    }

    private fun remoteAction(): AnAction = object :
        ToggleAction("Remote control", "Drive this conversation from another device", AllIcons.General.Web),
        DumbAware {

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun isSelected(event: AnActionEvent): Boolean =
            controller.conversation.bridgeState == CONNECTED

        override fun setSelected(event: AnActionEvent, state: Boolean) {
            if (state) connectRemote() else controller.setRemoteControl(false)
        }
    }

    private fun connectRemote() {
        val dialog = RemoteControlDialog(project)
        remoteDialog = dialog
        Disposer.register(dialog.disposable, Disposable { remoteDialog = null })
        controller.setRemoteControl(true)
        dialog.show()
    }

    fun showRemote() {
        val dialog = remoteDialog ?: return
        val url = controller.conversation.remoteUrl
        if (url != null) dialog.showUrl(url) else dialog.showFailure()
    }

    private fun compact() {
        val confirmed = Messages.showYesNoDialog(
            project,
            "Run the Claude Code built-in compaction on this conversation? " +
                "It summarises the earlier turns into a shorter form, so the agent keeps the summary " +
                "and loses the original wording of what was compacted.",
            "Compact Conversation",
            Messages.getQuestionIcon(),
        )
        if (confirmed != Messages.YES) return
        controller.sendPrompt(COMPACT)
    }

    private fun export() {
        val descriptor = FileSaverDescriptor("Export Conversation", "Save the transcript as Markdown", "md")
        val chosen = FileChooserFactory.getInstance()
            .createSaveFileDialog(descriptor, project)
            .save(null as Path?, controller.conversation.title + ".md") ?: return
        runCatching {
            Files.writeString(chosen.file.toPath(), TranscriptExport.toMarkdown(controller.conversation))
        }
    }

    private fun delete() {
        val confirmed = Messages.showYesNoDialog(
            project,
            "Delete " + controller.conversation.title + " and its transcript?",
            "Delete Conversation",
            Messages.getWarningIcon(),
        )
        if (confirmed != Messages.YES) return
        ConversationTabs.close(project, controller)
        ConversationManager.getInstance(project).delete(controller)
    }

    private fun action(text: String, description: String, icon: Icon, run: () -> Unit): AnAction =
        object : AnAction(text, description, icon), DumbAware {

            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

            override fun actionPerformed(event: AnActionEvent) = run()
        }

    private companion object {
        const val PLACE = "ChiselConversationToolbar"
        const val CONTINUE = "Continue"
        const val COMPACT = "/compact"
        const val CONNECTED = "connected"
    }
}
