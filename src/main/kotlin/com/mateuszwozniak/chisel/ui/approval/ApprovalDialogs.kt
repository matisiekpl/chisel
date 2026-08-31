package com.mateuszwozniak.chisel.ui.approval

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.mateuszwozniak.chisel.model.AgentMode
import com.mateuszwozniak.chisel.protocol.PermissionDecision
import com.mateuszwozniak.chisel.protocol.PermissionRequest
import com.mateuszwozniak.chisel.protocol.WriteToolInput
import com.mateuszwozniak.chisel.protocol.string
import com.mateuszwozniak.chisel.service.ConversationController
import com.mateuszwozniak.chisel.service.FeedbackFormatter
import com.mateuszwozniak.chisel.service.PermissionRouter
import com.mateuszwozniak.chisel.util.ProjectPaths
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class ApprovalDialogs(private val project: Project) : PermissionRouter {

    private val openDialogs = ConcurrentHashMap<String, DialogWrapper>()
    private val cancelled = Collections.synchronizedSet(mutableSetOf<String>())

    override fun handle(
        controller: ConversationController,
        request: PermissionRequest,
        respond: (PermissionDecision) -> Unit,
    ) {
        if (request.toolName == EXIT_PLAN_MODE) {
            showPlan(controller, request, respond)
            return
        }
        val writeInput = WriteToolInput.parse(request.toolName, request.input)
        if (writeInput != null && controller.conversation.mode == AgentMode.PLAN) {
            respond(PermissionDecision.Deny(PLAN_MODE_DENIAL))
            return
        }
        if (writeInput != null) showEdit(request, writeInput, respond)
        else showCommand(request, respond)
    }

    override fun cancel(requestId: String) {
        cancelled.add(requestId)
        onEventDispatchThread {
            openDialogs.remove(requestId)?.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
    }

    private fun showEdit(
        request: PermissionRequest,
        input: WriteToolInput,
        respond: (PermissionDecision) -> Unit,
    ) {
        onEventDispatchThread {
            val displayPath = ProjectPaths.relative(project.basePath, input.filePath)
            val dialog = EditApprovalDialog(project, input, displayPath)
            val decision = withDialog(request.requestId, dialog) {
                val formatter = FeedbackFormatter(displayPath)
                when (dialog.outcome) {
                    EditApprovalDialog.Outcome.ACCEPT -> PermissionDecision.Allow

                    EditApprovalDialog.Outcome.FEEDBACK -> PermissionDecision.Deny(
                        formatter.format(dialog.annotations(), dialog.editedContent(), dialog.languageId())
                    )

                    EditApprovalDialog.Outcome.REJECT -> PermissionDecision.Deny(
                        formatter.formatRejection(),
                        interrupt = true,
                    )
                }
            } ?: return@onEventDispatchThread
            sendDecision(respond, decision)
        }
    }

    private fun showCommand(request: PermissionRequest, respond: (PermissionDecision) -> Unit) {
        onEventDispatchThread {
            val dialog = CommandApprovalDialog(
                project,
                request.toolName,
                describe(request),
                request.decisionReason,
            )
            val decision = withDialog(request.requestId, dialog) {
                when (dialog.outcome) {
                    CommandApprovalDialog.Outcome.ALLOW -> PermissionDecision.Allow
                    CommandApprovalDialog.Outcome.DENY -> PermissionDecision.Deny(
                        dialog.denyReason().ifEmpty { DEFAULT_DENIAL }
                    )
                }
            } ?: return@onEventDispatchThread
            sendDecision(respond, decision)
        }
    }

    private fun showPlan(
        controller: ConversationController,
        request: PermissionRequest,
        respond: (PermissionDecision) -> Unit,
    ) {
        onEventDispatchThread {
            val plan = request.input.string("plan").orEmpty()
            val dialog = PlanApprovalDialog(project, plan)
            val outcome = withDialog(request.requestId, dialog) { dialog.outcome }
                ?: return@onEventDispatchThread
            if (outcome == PlanApprovalDialog.Outcome.IMPLEMENT) {
                sendDecision(respond, PermissionDecision.Allow)
                controller.changeMode(AgentMode.IMPLEMENTATION)
                return@onEventDispatchThread
            }
            val notes = dialog.notes().ifEmpty { KEEP_PLANNING_DENIAL }
            sendDecision(respond, PermissionDecision.Deny(notes))
        }
    }

    private fun <T> withDialog(requestId: String, dialog: DialogWrapper, read: () -> T): T? {
        openDialogs[requestId] = dialog
        dialog.show()
        openDialogs.remove(requestId)
        if (cancelled.remove(requestId)) return null
        return read()
    }

    private fun describe(request: PermissionRequest): String {
        request.input.string("command")?.let { command ->
            val description = request.input.string("description")
            return if (description == null) command else "$command\n\n$description"
        }
        return request.input.entrySet().joinToString("\n") { entry ->
            val value = if (entry.value.isJsonPrimitive) entry.value.asString else entry.value.toString()
            "${entry.key}: $value"
        }
    }

    private fun sendDecision(respond: (PermissionDecision) -> Unit, decision: PermissionDecision) {
        ApplicationManager.getApplication().executeOnPooledThread { respond(decision) }
    }

    private fun onEventDispatchThread(action: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(action, ModalityState.defaultModalityState())
    }

    private companion object {

        const val EXIT_PLAN_MODE = "ExitPlanMode"

        const val PLAN_MODE_DENIAL =
            "Plan mode is active, so writing files is not allowed. Keep exploring and " +
                "refining the plan; call ExitPlanMode when the plan is ready."

        const val KEEP_PLANNING_DENIAL =
            "I have not approved this plan yet. Keep refining it and call ExitPlanMode again."

        const val DEFAULT_DENIAL = "I did not allow this call. Try a different approach."
    }
}
