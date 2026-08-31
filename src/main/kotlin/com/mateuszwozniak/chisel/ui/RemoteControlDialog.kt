package com.mateuszwozniak.chisel.ui

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapper.IdeModalityType
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

class RemoteControlDialog(project: Project) : DialogWrapper(project, true, IdeModalityType.MODELESS) {

    private val status = JBLabel(CONNECTING, AnimatedIcon.Default.INSTANCE, SwingConstants.LEFT)

    private val link = JBTextField().apply {
        isEditable = false
        isVisible = false
    }

    private var url: String? = null

    private val copyAction = object : DialogWrapperAction("Copy link") {
        override fun doAction(event: ActionEvent) {
            url?.let { CopyPasteManager.getInstance().setContents(StringSelection(it)) }
        }
    }

    private val openAction = object : DialogWrapperAction("Open in browser") {
        override fun doAction(event: ActionEvent) {
            url?.let { BrowserUtil.browse(it) }
        }
    }

    private val closeAction = object : DialogWrapperAction("Close") {
        override fun doAction(event: ActionEvent) = close(OK_EXIT_CODE)
    }

    init {
        title = "Remote Control"
        closeAction.putValue(DEFAULT_ACTION, true)
        copyAction.isEnabled = false
        openAction.isEnabled = false
        init()
    }

    fun showUrl(value: String) {
        if (url == value) return
        url = value
        status.icon = null
        status.text = READY
        link.text = value
        link.isVisible = true
        copyAction.isEnabled = true
        openAction.isEnabled = true
        pack()
    }

    fun showFailure() {
        if (url != null) return
        status.icon = null
        status.text = FAILED
    }

    override fun createActions(): Array<Action> = arrayOf(copyAction, openAction, closeAction)

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, JBUI.scale(8)))
        panel.preferredSize = Dimension(JBUI.scale(WIDTH), JBUI.scale(HEIGHT))
        panel.add(status, BorderLayout.NORTH)
        panel.add(link, BorderLayout.CENTER)
        return panel
    }

    private companion object {
        const val WIDTH = 460
        const val HEIGHT = 80
        const val CONNECTING = "Connecting this session to your account…"
        const val READY = "Open this session on another device. Messages sent there arrive in this conversation."
        const val FAILED = "Could not start remote control. See the conversation for details."
    }
}
