package com.mateuszwozniak.chisel.cli

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.util.SystemInfo
import com.mateuszwozniak.chisel.protocol.number
import com.mateuszwozniak.chisel.protocol.obj
import com.mateuszwozniak.chisel.protocol.string
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

object ClaudeCredentials {

    fun accessToken(): String? {
        val oauth = read()?.obj("claudeAiOauth") ?: return null
        val expiresAt = oauth.number("expiresAt")?.toLong()
        if (expiresAt != null && expiresAt <= System.currentTimeMillis()) return null
        return oauth.string("accessToken")?.takeIf { it.isNotBlank() }
    }

    private fun read(): JsonObject? {
        val payload = fromKeychain() ?: fromFile() ?: return null
        return runCatching { JsonParser.parseString(payload) as? JsonObject }.getOrNull()
    }

    private fun fromKeychain(): String? {
        if (!SystemInfo.isMac) return null
        val command = GeneralCommandLine(
            "security",
            "find-generic-password",
            "-s",
            KEYCHAIN_SERVICE,
            "-a",
            System.getProperty("user.name"),
            "-w",
        ).withCharset(StandardCharsets.UTF_8)
        val output = runCatching { CapturingProcessHandler(command).runProcess(TIMEOUT) }.getOrNull()
        if (output == null || output.exitCode != 0) return null
        return output.stdout.trim().takeIf { it.isNotEmpty() }
    }

    private fun fromFile(): String? {
        val file = Paths.get(System.getProperty("user.home"), ".claude", ".credentials.json")
        if (!Files.isReadable(file)) return null
        return runCatching { Files.readString(file) }.getOrNull()
    }

    private const val KEYCHAIN_SERVICE = "Claude Code-credentials"

    private const val TIMEOUT = 5_000
}
