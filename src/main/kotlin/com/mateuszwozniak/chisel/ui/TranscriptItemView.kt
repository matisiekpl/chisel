package com.mateuszwozniak.chisel.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.mateuszwozniak.chisel.model.TranscriptItem
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicLong
import javax.swing.JComponent
import javax.swing.JPanel

class TranscriptItemView(
    private val html: TranscriptHtml,
    val item: TranscriptItem,
    parent: Disposable,
) : JPanel(BorderLayout()) {

    private val style = styleOf(item)
    private val badge = if (style == Style.BADGE) Badge() else null
    private val view = if (style == Style.BADGE) null else HtmlView(parent)
    private val generation = AtomicLong()

    init {
        isOpaque = false
        border = when (style) {
            Style.BUBBLE -> JBUI.Borders.empty(10, MARGIN + 12)
            Style.BADGE -> JBUI.Borders.empty(1, 12)
            Style.PLAIN -> JBUI.Borders.empty(2, 12)
        }
        add(content(), BorderLayout.CENTER)
        refresh()
    }

    override fun getPreferredSize(): Dimension {
        val pane = view?.component ?: return super.getPreferredSize()
        val available = parent?.width ?: 0
        if (available <= 0) return super.getPreferredSize()
        val padding = insets
        pane.setSize(available - padding.left - padding.right, Short.MAX_VALUE.toInt())
        return Dimension(available, pane.preferredSize.height + padding.top + padding.bottom)
    }

    override fun paintComponent(graphics: Graphics) {
        if (style != Style.BUBBLE) return super.paintComponent(graphics)
        val canvas = graphics.create() as Graphics2D
        canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val arc = JBUI.scale(ARC)
        val margin = JBUI.scale(MARGIN)
        val span = width - 2 * margin
        canvas.color = UIUtil.getTextFieldBackground()
        canvas.fillRoundRect(margin, 0, span, height, arc, arc)
        canvas.color = JBColor.border()
        canvas.drawRoundRect(margin, 0, span - 1, height - 1, arc, arc)
        canvas.dispose()
    }

    fun refresh() {
        val chip = badge
        if (chip != null) {
            chip.show(badgeTitle(), badgeDetail())
            return
        }
        val version = generation.incrementAndGet()
        ApplicationManager.getApplication().executeOnPooledThread {
            val rendered = runCatching { html.render(item) }.getOrElse { "" }
            ApplicationManager.getApplication().invokeLater(
                { if (generation.get() == version) view?.setHtml(rendered) },
                ModalityState.defaultModalityState(),
            )
        }
    }

    fun onClicked(action: () -> Unit) {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) = action()
        })
        badge?.onClicked(action)
        view?.onClicked(action)
    }

    private fun content(): JComponent {
        val chip = badge ?: return view!!.component
        val row = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        row.isOpaque = false
        row.add(chip)
        return row
    }

    private fun badgeTitle(): String = when (item) {
        is TranscriptItem.Thinking -> "Thinking"
        is TranscriptItem.ToolCall -> item.name
        else -> ""
    }

    private fun badgeDetail(): String = when (item) {
        is TranscriptItem.Thinking -> html.shorten(item.text)
        is TranscriptItem.ToolCall -> toolDetail(item)
        else -> ""
    }

    private fun toolDetail(call: TranscriptItem.ToolCall): String {
        val parts = mutableListOf<String>()
        html.toolSummary(call.name, call.input).takeIf { it.isNotEmpty() }?.let { parts.add(html.shorten(it)) }
        when {
            call.result == null -> parts.add("running…")
            call.failed -> parts.add("failed")
        }
        return parts.joinToString("  ·  ")
    }

    private fun styleOf(item: TranscriptItem): Style = when (item) {
        is TranscriptItem.UserPrompt -> Style.BUBBLE
        is TranscriptItem.Thinking, is TranscriptItem.ToolCall -> Style.BADGE
        else -> Style.PLAIN
    }

    private enum class Style { BUBBLE, BADGE, PLAIN }

    private class Badge : JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)) {

        private val title = JBLabel()
        private val detail = JBLabel()

        init {
            isOpaque = false
            border = JBUI.Borders.empty(6, 8)
            title.font = JBUI.Fonts.smallFont().asBold()
            detail.font = JBUI.Fonts.smallFont()
            detail.foreground = UIUtil.getContextHelpForeground()
            add(title)
            add(detail)
        }

        fun show(name: String, text: String) {
            title.text = name
            detail.text = text
            detail.isVisible = text.isNotEmpty()
            revalidate()
            repaint()
        }

        fun onClicked(action: () -> Unit) {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(event: MouseEvent) = action()
            })
        }

        override fun paintComponent(graphics: Graphics) {
            val canvas = graphics.create() as Graphics2D
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val arc = JBUI.scale(BADGE_ARC)
            canvas.color = UIUtil.getTextFieldBackground()
            canvas.fillRoundRect(0, 0, width, height, arc, arc)
            canvas.color = JBColor.border()
            canvas.stroke = BasicStroke(
                1f,
                BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_ROUND,
                1f,
                floatArrayOf(JBUI.scale(DASH).toFloat(), JBUI.scale(DASH).toFloat()),
                0f,
            )
            canvas.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
            canvas.dispose()
        }

        private companion object {
            const val BADGE_ARC = 10
            const val DASH = 3
        }
    }

    private companion object {
        const val ARC = 12
        const val MARGIN = 12
    }
}
