package com.mateuszwozniak.chisel.ui.approval

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.DiffRequestPanel
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.JBUI
import com.mateuszwozniak.chisel.model.LineAnnotation
import com.mateuszwozniak.chisel.protocol.WriteToolInput
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.Action
import javax.swing.JComponent

class EditApprovalDialog(
    project: Project,
    private val input: WriteToolInput,
    private val displayPath: String,
) : DialogWrapper(project, true) {

    enum class Outcome { ACCEPT, FEEDBACK, REJECT }

    private val model = AnnotationModel()
    private val proposedDocument: Document
    private val originalProposed: String
    private val diffPanel: DiffRequestPanel

    private val acceptAction = object : DialogWrapperAction("Accept") {
        override fun doAction(event: ActionEvent) {
            outcome = Outcome.ACCEPT
            close(OK_EXIT_CODE)
        }
    }

    private val feedbackAction = object : DialogWrapperAction("Send feedback") {
        override fun doAction(event: ActionEvent) {
            outcome = Outcome.FEEDBACK
            close(OK_EXIT_CODE)
        }
    }

    private val rejectAction = object : DialogWrapperAction("Reject and stop") {
        override fun doAction(event: ActionEvent) {
            outcome = Outcome.REJECT
            close(CANCEL_EXIT_CODE)
        }
    }

    var outcome: Outcome = Outcome.REJECT
        private set

    init {
        title = "Review write: $displayPath"
        val fileText = readCurrentText()
        val fileType = FileTypeManager.getInstance().getFileTypeByFileName(displayPath)
        val factory = DiffContentFactory.getInstance()
        val currentContent = factory.create(project, input.currentSide(fileText), fileType)
        val proposedContent = factory.createEditable(project, input.proposedSide(fileText), fileType)
        proposedDocument = proposedContent.document
        originalProposed = proposedDocument.text

        val request = SimpleDiffRequest(displayPath, currentContent, proposedContent, "Current", "Proposed")
        request.putUserData(DiffUserDataKeys.PLACE, AnnotationKeys.DIFF_PLACE)
        request.putUserData(DiffUserDataKeys.ALIGNED_TWO_SIDED_DIFF, false)
        request.putUserData(AnnotationKeys.MODEL, model)

        diffPanel = DiffManager.getInstance().createRequestPanel(project, disposable, null)
        diffPanel.setRequest(request)

        model.onChanged = { updateFeedbackAvailability() }
        proposedDocument.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) = updateFeedbackAvailability()
        }, disposable)

        init()
        updateFeedbackAvailability()
    }

    fun annotations(): List<LineAnnotation> = model.all()

    fun editedContent(): String? = proposedDocument.text.takeIf { it != originalProposed }

    fun languageId(): String = displayPath.substringAfterLast('.', "")

    override fun createCenterPanel(): JComponent {
        val component = diffPanel.component
        component.preferredSize = Dimension(JBUI.scale(WIDTH), JBUI.scale(HEIGHT))
        return component
    }

    override fun getPreferredFocusedComponent(): JComponent? = diffPanel.preferredFocusedComponent

    override fun createActions(): Array<Action> = arrayOf(acceptAction, feedbackAction, rejectAction)

    override fun getDimensionServiceKey(): String = "Chisel.EditApproval"

    private fun updateFeedbackAvailability() {
        feedbackAction.isEnabled = model.hasAny() || proposedDocument.text != originalProposed
    }

    private fun readCurrentText(): String =
        runCatching { Files.readString(Paths.get(input.filePath)) }.getOrDefault("")

    private companion object {
        const val WIDTH = 1000
        const val HEIGHT = 640
    }
}
