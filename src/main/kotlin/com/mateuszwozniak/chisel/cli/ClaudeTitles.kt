package com.mateuszwozniak.chisel.cli

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.mateuszwozniak.chisel.model.AgentModel
import java.nio.charset.StandardCharsets

object ClaudeTitles {

    fun generate(prompt: String, workingDirectory: String): String? {
        val executable = ClaudeExecutable.locate() ?: return null
        val command = GeneralCommandLine(
            executable.toString(),
            "-p",
            "--model",
            AgentModel.HAIKU.alias,
            "--no-session-persistence",
            "--tools",
            "",
        )
            .withWorkDirectory(workingDirectory)
            .withCharset(StandardCharsets.UTF_8)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        val output = runCatching {
            val handler = CapturingProcessHandler(command)
            handler.processInput.use { it.write((INSTRUCTION + prompt).toByteArray(StandardCharsets.UTF_8)) }
            handler.runProcess(TIMEOUT)
        }.getOrNull()
        if (output == null || output.exitCode != 0) return null
        return output.stdout.lineSequence().firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.trim('"', '\'', '.', '*', '#', ' ')
            ?.takeIf { it.isNotEmpty() }
    }

    private const val TIMEOUT = 30_000

    private const val INSTRUCTION =
        "Write a 3-6 word title for the conversation that starts with the message below, " +
            "in the language of that message. Return the title only, without quotes or a period.\n\n"
}
