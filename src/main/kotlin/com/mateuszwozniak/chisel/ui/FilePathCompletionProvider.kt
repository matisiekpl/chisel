package com.mateuszwozniak.chisel.ui

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.lookup.CharFilter
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.util.textCompletion.TextCompletionProvider

class FilePathCompletionProvider(private val project: Project) : TextCompletionProvider {

    override fun getAdvertisement(): String? = null

    override fun getPrefix(text: String, offset: Int): String? {
        val start = text.lastIndexOf('@', offset - 1)
        if (start < 0) return null
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

    private companion object {
        const val RESULT_LIMIT = 200
    }
}
