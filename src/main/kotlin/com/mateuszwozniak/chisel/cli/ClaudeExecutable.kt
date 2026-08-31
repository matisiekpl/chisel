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

    fun missingMessage(): String =
        "Claude Code CLI was not found. Install it, then run 'claude auth login' in a terminal " +
            "so the plugin can start a session with your own subscription."
}
