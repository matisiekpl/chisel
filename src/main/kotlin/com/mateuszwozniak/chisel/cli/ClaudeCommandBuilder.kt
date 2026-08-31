package com.mateuszwozniak.chisel.cli

import com.intellij.execution.configurations.GeneralCommandLine
import com.mateuszwozniak.chisel.model.AgentMode
import com.mateuszwozniak.chisel.model.SessionOptions
import com.mateuszwozniak.chisel.util.ChiselPaths
import java.nio.charset.StandardCharsets
import java.nio.file.Path

class ClaudeCommandBuilder(
    private val executable: Path,
    private val workingDirectory: String,
) {

    fun build(options: SessionOptions, start: SessionStart): GeneralCommandLine {
        val commandLine = GeneralCommandLine(executable.toString())
            .withWorkDirectory(workingDirectory)
            .withCharset(StandardCharsets.UTF_8)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withEnvironment(CHECKPOINTING_VARIABLE, "true")

        commandLine.addParameters(BASE_PARAMETERS)
        ChiselPaths.ensureRoot()?.let { commandLine.addParameters("--add-dir", it.toString()) }
        commandLine.addParameters("--permission-mode", options.mode.permissionMode)
        commandLine.addParameters("--allowedTools", AUTO_APPROVED_TOOLS)
        commandLine.addParameters("--model", options.model.alias)
        commandLine.addParameters("--effort", options.effort.value)
        if (options.mode == AgentMode.IMPLEMENTATION) {
            commandLine.addParameters("--append-system-prompt", READABLE_COMMANDS)
        }
        if (options.effort.ultracode) {
            commandLine.addParameters("--settings", ULTRACODE_SETTINGS)
        }
        commandLine.addParameters(sessionParameters(start))
        return commandLine
    }

    private fun sessionParameters(start: SessionStart): List<String> = when (start) {
        is SessionStart.Fresh -> listOf("--session-id", start.sessionId)
        is SessionStart.Resume -> listOf("--resume", start.sessionId)
        is SessionStart.ResumeAt ->
            listOf("--resume", start.sessionId, "--resume-session-at", start.messageUuid)
    }

    companion object {

        const val CHECKPOINTING_VARIABLE = "CLAUDE_CODE_ENABLE_SDK_FILE_CHECKPOINTING"

        private const val ULTRACODE_SETTINGS = "{\"ultracode\":true}"

        private const val READABLE_COMMANDS =
            "A developer reads every shell command you run, in a review dialog, before approving it. " +
                "Write commands so that reading one tells them exactly what will happen.\n" +
                "- Run one command per step, each doing a single thing. Do not chain unrelated work " +
                "with && or ; into a single call.\n" +
                "- Prefer a plain, obvious command over a clever one-liner. Split long pipelines into " +
                "separate calls when the intermediate result is worth seeing.\n" +
                "- Use long option names where they make the intent clearer.\n" +
                "- Give every Bash call a description that says what it does in plain words.\n" +
                "This applies to how you write commands, not to how much you get done: keep working " +
                "through the task, just in steps a reader can follow."

        private const val AUTO_APPROVED_TOOLS =
            "Read,Glob,Grep,WebFetch,WebSearch,TodoWrite,Task"

        private val BASE_PARAMETERS = listOf(
            "--print",
            "--verbose",
            "--input-format", "stream-json",
            "--output-format", "stream-json",
            "--include-partial-messages",
            "--replay-user-messages",
            "--forward-subagent-text",
            "--permission-prompt-tool", "stdio",
        )
    }
}
