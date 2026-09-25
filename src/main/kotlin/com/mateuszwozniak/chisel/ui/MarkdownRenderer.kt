package com.mateuszwozniak.chisel.ui

import com.intellij.markdown.utils.lang.CodeBlockHtmlSyntaxHighlighter
import com.intellij.markdown.utils.lang.HtmlSyntaxHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkdownParser
import java.net.URI

class MarkdownRenderer(project: Project) {

    private val flavour = HighlightedFlavour(CodeBlockHtmlSyntaxHighlighter(project))

    fun render(markdown: String): String {
        val text = wrapFences(markdown)
        val tree = MarkdownParser(flavour).buildMarkdownTreeFromString(text)
        val providers = flavour.createHtmlGeneratingProviders(LinkMap.buildLinkMap(tree, text), null)
        return HtmlGenerator(text, tree, providers, false).generateHtml()
    }

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
                (MarkdownElementTypes.CODE_FENCE to CodeFenceProvider(highlighter))
    }

    private class CodeFenceProvider(
        private val highlighter: HtmlSyntaxHighlighter,
    ) : GeneratingProvider {

        override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
            var language: String? = null
            val content = StringBuilder()

            val children = node.children.iterator()
            while (children.hasNext()) {
                val child = children.next()
                when (child.type) {
                    MarkdownTokenTypes.FENCE_LANG -> {
                        language = HtmlGenerator.leafText(text, child).toString().trim()
                        if (children.hasNext()) children.next()
                    }

                    MarkdownTokenTypes.CODE_FENCE_CONTENT, MarkdownTokenTypes.EOL ->
                        content.append(HtmlGenerator.trimIndents(child.getTextInNode(text), 0))

                    MarkdownTokenTypes.CODE_FENCE_END -> break
                }
            }

            val code = HtmlChunk.tag("code").let { tag ->
                language?.let { tag.setClass("language-" + it.split(" ").joinToString("-")) } ?: tag
            }
            val html = HtmlBuilder()
                .append(highlighter.color(language, content.toString()))
                .wrapWith(code)
                .wrapWith(HtmlChunk.tag("pre"))
            visitor.consumeHtml(html.toString())
        }
    }
}
