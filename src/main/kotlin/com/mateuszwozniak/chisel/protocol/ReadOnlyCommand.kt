package com.mateuszwozniak.chisel.protocol

import com.google.gson.JsonObject

object ReadOnlyCommand {

    fun matches(toolName: String, input: JsonObject): Boolean {
        if (toolName != TOOL_NAME) return false
        val command = input.string("command")?.trim().orEmpty()
        if (command.isEmpty()) return false
        val segments = split(command) ?: return false
        return segments.isNotEmpty() && segments.all(::readsOnly)
    }

    private fun split(command: String): List<String>? {
        val segments = mutableListOf(StringBuilder())
        var quote: Char? = null
        var index = 0
        while (index < command.length) {
            val character = command[index]
            if (quote != null) {
                if (character == quote) quote = null
                segments.last().append(character)
                index++
                continue
            }
            when {
                character == '\'' || character == '"' -> {
                    quote = character
                    segments.last().append(character)
                }

                character == '\\' -> {
                    segments.last().append(character)
                    index++
                    if (index < command.length) segments.last().append(command[index])
                }

                character in FORBIDDEN -> return null

                character in SEPARATORS -> segments.add(StringBuilder())

                else -> segments.last().append(character)
            }
            index++
        }
        if (quote != null) return null
        return segments.map { segment -> segment.toString().trim() }.filter { segment -> segment.isNotEmpty() }
    }

    private fun readsOnly(segment: String): Boolean {
        val tokens = segment.split(WHITESPACE).filter { token -> token.isNotEmpty() }
        val name = tokens.firstOrNull()?.substringAfterLast('/') ?: return false
        if (name !in COMMANDS) return false
        val flags = tokens.drop(1).filter { token -> token.startsWith("-") }
        return when (name) {
            "sed" -> flags.none { flag ->
                if (flag.startsWith("--")) flag.startsWith("--in-place") else flag.contains("i")
            }

            "find" -> flags.none { flag -> flag in DESTRUCTIVE_FIND_FLAGS }
            else -> true
        }
    }

    private const val TOOL_NAME = "Bash"

    private val COMMANDS = setOf(
        "grep", "egrep", "fgrep", "rg", "sed", "cat", "head", "tail",
        "wc", "cut", "find", "ls", "tree", "file", "stat",
    )

    private val DESTRUCTIVE_FIND_FLAGS = setOf("-exec", "-execdir", "-ok", "-okdir", "-delete", "-fprint", "-fprintf")

    private val FORBIDDEN = setOf('>', '<', '`', '(', ')', '{', '}')

    private val SEPARATORS = setOf('|', '&', ';', '\n')

    private val WHITESPACE = Regex("\\s+")
}
