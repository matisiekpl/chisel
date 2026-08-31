package com.mateuszwozniak.chisel.ui.approval

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.DiffRequestPanel
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapper.IdeModalityType
import com.intellij.util.ui.JBUI
import com.mateuszwozniak.chisel.model.LineAnnotation
import com.mateuszwozniak.chisel.protocol.WriteToolInput
import com.mateuszwozniak.chisel.ui.Shortcuts
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
) : DialogWrapper(project, true, IdeModalityType.MODELESS) {

    enum class Outcome { ACCEPT, FEEDBACK, REJECT }

    private val model = AnnotationModel()
    private val fileText: String
    private val proposedDocument: Document
    private val originalProposed: String
    private val diffPanel: DiffRequestPanel

    private val submitAction =
        object : DialogWrapperAction(Shortcuts.labelled(ACCEPT_LABEL, Shortcuts.submitLabel())) {
            override fun doAction(event: ActionEvent) = submit()
        }

    private val rejectAction =
        object : DialogWrapperAction(Shortcuts.labelled("Reject", Shortcuts.rejectLabel())) {
            override fun doAction(event: ActionEvent) = doCancelAction()
        }

    private val commentAction =
        object : DialogWrapperAction(Shortcuts.labelled(COMMENT_LABEL, Shortcuts.commentLabel())) {
            override fun doAction(event: ActionEvent) = model.requestComment()
        }

    private val revertAction =
        object : DialogWrapperAction(Shortcuts.labelled("Revert changes", Shortcuts.revertLabel())) {
            override fun doAction(event: ActionEvent) = revert()
        }

    var outcome: Outcome = Outcome.REJECT
        private set

    init {
        title = "Review write: $displayPath"
        fileText = readCurrentText()
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

        model.onChanged = { updateActions() }
        proposedDocument.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) = updateActions()
        }, disposable)

        submitAction.putValue(DEFAULT_ACTION, true)
        init()
        Shortcuts.install(rootPane, Shortcuts.submit()) { submit() }
        Shortcuts.install(rootPane, Shortcuts.revert()) { revert() }
        Shortcuts.install(rootPane, Shortcuts.reject()) { doCancelAction() }
        updateActions()
    }

    override fun doCancelAction() {
        outcome = Outcome.REJECT
        super.doCancelAction()
    }

    private fun submit() {
        outcome = if (hasChanges()) Outcome.FEEDBACK else Outcome.ACCEPT
        close(OK_EXIT_CODE)
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

    override fun createActions(): Array<Action> = arrayOf(rejectAction, submitAction)

    override fun createLeftSideActions(): Array<Action> = arrayOf(commentAction, revertAction)

    override fun getDimensionServiceKey(): String = "Chisel.EditApproval"

    private fun updateActions() {
        val changed = hasChanges()
        submitAction.putValue(
            Action.NAME,
            Shortcuts.labelled(if (changed) ADJUST_LABEL else ACCEPT_LABEL, Shortcuts.submitLabel()),
        )
        getButton(revertAction)?.isVisible = changed
        commentAction.putValue(
            Action.NAME,
            Shortcuts.labelled(
                if (model.hasCommentAtCaret()) EDIT_COMMENT_LABEL else COMMENT_LABEL,
                Shortcuts.commentLabel(),
            ),
        )
    }

    private fun revert() {
        ApplicationManager.getApplication().runWriteAction {
            proposedDocument.setText(originalProposed)
        }
        model.clear()
    }

    private fun hasChanges(): Boolean = model.hasAny() || proposedDocument.text != originalProposed

    private fun readCurrentText(): String =
        runCatching { Files.readString(Paths.get(input.filePath)) }.getOrDefault("")

    private companion object {
        const val ACCEPT_LABEL = "Accept"
        const val COMMENT_LABEL = "Comment"
        const val EDIT_COMMENT_LABEL = "Edit comment"
        const val ADJUST_LABEL = "Adjust"
        const val WIDTH = 1000
        const val HEIGHT = 640
    }
}
