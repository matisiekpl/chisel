package com.mateuszwozniak.chisel.ui.approval

import com.google.gson.JsonObject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.mateuszwozniak.chisel.model.AgentMode
import com.mateuszwozniak.chisel.protocol.PermissionDecision
import com.mateuszwozniak.chisel.protocol.PermissionRequest
import com.mateuszwozniak.chisel.protocol.UserQuestion
import com.mateuszwozniak.chisel.protocol.WriteToolInput
import com.mateuszwozniak.chisel.protocol.string
import com.mateuszwozniak.chisel.service.ConversationController
import com.mateuszwozniak.chisel.service.FeedbackFormatter
import com.mateuszwozniak.chisel.service.PermissionRouter
import com.mateuszwozniak.chisel.state.ChiselSettings
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
        val questions = UserQuestion.parse(request.toolName, request.input)
        if (questions != null) {
            showQuestions(request, questions, respond)
            return
        }
        if (controller.conversation.mode == AgentMode.ASK) {
            respond(PermissionDecision.Deny(ASK_MODE_DENIAL))
            return
        }
        if (request.toolName == EXIT_PLAN_MODE) {
            showPlan(controller, request, respond)
            return
        }
        val writeInput = WriteToolInput.parse(request.toolName, request.input)
        if (writeInput != null && controller.conversation.mode == AgentMode.PLAN) {
            respond(PermissionDecision.Deny(PLAN_MODE_DENIAL))
            return
        }
        if (writeInput != null) {
            showEdit(request, writeInput, respond)
            return
        }
        if (allowedWhilePlanning(controller.conversation.mode, request.toolName)) {
            respond(PermissionDecision.Allow())
            return
        }
        showCommand(request, respond)
    }

    private fun allowedWhilePlanning(mode: AgentMode, toolName: String): Boolean =
        mode == AgentMode.PLAN &&
            toolName.startsWith(MCP_PREFIX) &&
            ChiselSettings.getInstance().allowMcpInPlanMode

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
            withDialog(request.requestId, dialog) {
                val formatter = FeedbackFormatter(displayPath)
                val decision = when (dialog.outcome) {
                    EditApprovalDialog.Outcome.ACCEPT -> PermissionDecision.Allow()

                    EditApprovalDialog.Outcome.FEEDBACK -> PermissionDecision.Deny(
                        formatter.format(dialog.annotations(), dialog.editedContent(), dialog.languageId())
                    )

                    EditApprovalDialog.Outcome.REJECT -> PermissionDecision.Deny(
                        formatter.formatRejection(),
                        interrupt = true,
                    )
                }
                sendDecision(respond, decision)
            }
        }
    }

    private fun showCommand(request: PermissionRequest, respond: (PermissionDecision) -> Unit) {
        onEventDispatchThread {
            val dialog = CommandApprovalDialog(
                project,
                request.toolName,
                commandOf(request),
                request.input.string("description") ?: request.decisionReason,
            )
            withDialog(request.requestId, dialog) {
                val decision = when (dialog.outcome) {
                    CommandApprovalDialog.Outcome.ALLOW -> PermissionDecision.Allow()
                    CommandApprovalDialog.Outcome.DENY -> PermissionDecision.Deny(DEFAULT_DENIAL)
                }
                sendDecision(respond, decision)
            }
        }
    }

    private fun showQuestions(
        request: PermissionRequest,
        questions: List<UserQuestion>,
        respond: (PermissionDecision) -> Unit,
    ) {
        onEventDispatchThread {
            val dialog = QuestionDialog(project, questions)
            withDialog(request.requestId, dialog) {
                val answers = dialog.answers()
                val decision = if (dialog.outcome == QuestionDialog.Outcome.ANSWER && answers.isNotEmpty()) {
                    PermissionDecision.Allow(answered(request.input, answers))
                } else {
                    PermissionDecision.Deny(QUESTION_DENIAL)
                }
                sendDecision(respond, decision)
            }
        }
    }

    private fun answered(input: JsonObject, answers: Map<String, String>): JsonObject {
        val collected = JsonObject()
        answers.forEach { (question, answer) -> collected.addProperty(question, answer) }
        return input.deepCopy().apply { add("answers", collected) }
    }

    private fun showPlan(
        controller: ConversationController,
        request: PermissionRequest,
        respond: (PermissionDecision) -> Unit,
    ) {
        onEventDispatchThread {
            val plan = request.input.string("plan").orEmpty()
            val dialog = PlanApprovalDialog(project, plan)
            withDialog(request.requestId, dialog) {
                if (dialog.outcome == PlanApprovalDialog.Outcome.IMPLEMENT) {
                    sendDecision(respond, PermissionDecision.Allow())
                    controller.changeMode(AgentMode.IMPLEMENTATION)
                    return@withDialog
                }
                val notes = dialog.notes().ifEmpty { KEEP_PLANNING_DENIAL }
                sendDecision(respond, PermissionDecision.Deny(notes))
            }
        }
    }

    private fun withDialog(requestId: String, dialog: DialogWrapper, onClosed: () -> Unit) {
        openDialogs[requestId] = dialog
        Disposer.register(dialog.disposable, Disposable {
            openDialogs.remove(requestId)
            if (!cancelled.remove(requestId)) onClosed()
        })
        dialog.show()
    }

    private fun commandOf(request: PermissionRequest): String =
        request.input.string("command") ?: request.input.entrySet().joinToString("\n") { entry ->
            val value = if (entry.value.isJsonPrimitive) entry.value.asString else entry.value.toString()
            "${entry.key}: $value"
        }

    private fun sendDecision(respond: (PermissionDecision) -> Unit, decision: PermissionDecision) {
        ApplicationManager.getApplication().executeOnPooledThread { respond(decision) }
    }

    private fun onEventDispatchThread(action: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(action, ModalityState.defaultModalityState())
    }

    private companion object {

        const val EXIT_PLAN_MODE = "ExitPlanMode"

        const val MCP_PREFIX = "mcp__"

        const val ASK_MODE_DENIAL =
            "Ask mode is active, so only read-only tools are available. Answer from what you " +
                "can read, and do not modify anything or write a plan."

        const val PLAN_MODE_DENIAL =
            "Plan mode is active, so writing files is not allowed. Keep exploring and " +
                "refining the plan; call ExitPlanMode when the plan is ready."

        const val KEEP_PLANNING_DENIAL =
            "I have not approved this plan yet. Keep refining it and call ExitPlanMode again."

        const val DEFAULT_DENIAL = "I did not allow this call. Try a different approach."

        const val QUESTION_DENIAL =
            "I skipped the question. Pick the approach you think is best and keep going."
    }
}
