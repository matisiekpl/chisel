package com.mateuszwozniak.chisel.cli

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Paths

object ClaudeConfiguration {

    fun read(): JsonObject? {
        val file = Paths.get(System.getProperty("user.home"), ".claude.json")
        if (!Files.isReadable(file)) return null
        return runCatching { JsonParser.parseString(Files.readString(file)) as? JsonObject }.getOrNull()
    }
}
