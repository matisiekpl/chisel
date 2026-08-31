package com.mateuszwozniak.chisel.ui

import com.intellij.markdown.utils.CodeFenceSyntaxHighlighterGeneratingProvider
import com.intellij.markdown.utils.MarkdownToHtmlConverter
import com.intellij.markdown.utils.lang.CodeBlockHtmlSyntaxHighlighter
import com.intellij.markdown.utils.lang.HtmlSyntaxHighlighter
import com.intellij.openapi.project.Project
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.parser.LinkMap
import java.net.URI

class MarkdownRenderer(project: Project) {

    private val converter = MarkdownToHtmlConverter(
        HighlightedFlavour(CodeBlockHtmlSyntaxHighlighter(project)),
    )

    fun render(markdown: String): String = converter.convertMarkdownToHtml(wrapFences(markdown))

    private fun wrapFences(markdown: String): String {
        var inside = false
        return markdown.lineSequence().joinToString("\n") { line ->
            when {
                line.trimStart().startsWith(FENCE) -> {
                    inside = !inside
                    line
                }

                inside && line.length > WRAP_LIMIT -> line.chunked(WRAP_LIMIT).joinToString("\n")

                else -> line
            }
        }
    }

    private companion object {
        const val FENCE = "```"
        const val WRAP_LIMIT = 100
    }

    private class HighlightedFlavour(
        private val highlighter: HtmlSyntaxHighlighter,
    ) : GFMFlavourDescriptor() {

        override fun createHtmlGeneratingProviders(
            linkMap: LinkMap,
            baseURI: URI?,
        ): Map<IElementType, GeneratingProvider> =
            super.createHtmlGeneratingProviders(linkMap, baseURI) +
                (MarkdownElementTypes.CODE_FENCE to CodeFenceSyntaxHighlighterGeneratingProvider(highlighter))
    }
}
