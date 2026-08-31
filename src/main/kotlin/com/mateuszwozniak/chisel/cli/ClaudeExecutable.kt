package com.mateuszwozniak.chisel.cli

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object ClaudeExecutable {

    private val FALLBACK_PATHS = listOf(
        ".local/bin/claude",
        ".claude/local/claude",
        ".bun/bin/claude",
    )

    fun locate(): Path? {
        PathEnvironmentVariableUtil.findInPath("claude")?.let { return it.toPath() }
        val home = Paths.get(System.getProperty("user.home"))
        return FALLBACK_PATHS
            .map { home.resolve(it) }
            .firstOrNull { Files.isExecutable(it) }
    }

    fun missingMessage(): String = "$MISSING_HEADLINE. $MISSING_DETAIL"

    const val MISSING_HEADLINE = "Claude Code CLI was not found"

    const val MISSING_DETAIL =
        "Chisel drives the CLI installed on this machine, so it cannot run without it"

    const val MISSING_HINT =
        "Install Claude Code, run claude in a terminal to sign in, then reopen this tool window"
}
