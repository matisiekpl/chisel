package com.mateuszwozniak.chisel.ui

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.lookup.CharFilter
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.util.textCompletion.TextCompletionProvider

class FilePathCompletionProvider(
    private val project: Project,
    private val commands: () -> List<String>,
) : TextCompletionProvider {

    override fun getAdvertisement(): String? = null

    override fun getPrefix(text: String, offset: Int): String? {
        commandPrefix(text, offset)?.let { return COMMAND_MARKER + it }
        val start = text.lastIndexOf('@', offset - 1)
        if (start < 0) return null
        val fragment = text.substring(start + 1, offset)
        return if (fragment.any { it.isWhitespace() }) null else fragment
    }

    private fun commandPrefix(text: String, offset: Int): String? {
        val start = text.lastIndexOf('/', offset - 1)
        if (start < 0) return null
        if (text.take(start).any { !it.isWhitespace() }) return null
        val fragment = text.substring(start + 1, offset)
        return if (fragment.any { it.isWhitespace() }) null else fragment
    }

    override fun applyPrefixMatcher(
        result: CompletionResultSet,
        prefix: String,
    ): CompletionResultSet = result.withPrefixMatcher(PlainPrefixMatcher(prefix))

    override fun acceptChar(character: Char): CharFilter.Result =
        if (character.isWhitespace()) CharFilter.Result.HIDE_LOOKUP
        else CharFilter.Result.ADD_TO_PREFIX

    override fun fillCompletionVariants(
        parameters: CompletionParameters,
        prefix: String,
        result: CompletionResultSet,
    ) {
        if (prefix.startsWith(COMMAND_MARKER)) {
            fillCommands(prefix.removePrefix(COMMAND_MARKER), result)
            return
        }
        val base = project.basePath ?: return
        var added = 0
        ProjectFileIndex.getInstance(project).iterateContent { file ->
            if (!file.isDirectory) {
                val path = file.path.removePrefix(base).trimStart('/')
                if (path.contains(prefix, ignoreCase = true)) {
                    result.addElement(LookupElementBuilder.create(path).withIcon(file.fileType.icon))
                    added++
                }
            }
            added < RESULT_LIMIT
        }
        result.stopHere()
    }

    private fun fillCommands(prefix: String, result: CompletionResultSet) {
        val matcher = result.withPrefixMatcher(PlainPrefixMatcher(prefix))
        commands()
            .filter { it.contains(prefix, ignoreCase = true) }
            .take(RESULT_LIMIT)
            .forEach { command ->
                matcher.addElement(
                    LookupElementBuilder.create(command).withIcon(AllIcons.Actions.Execute)
                )
            }
        matcher.stopHere()
    }

    private companion object {
        const val RESULT_LIMIT = 200
        const val COMMAND_MARKER = "\u0000"
    }
}
