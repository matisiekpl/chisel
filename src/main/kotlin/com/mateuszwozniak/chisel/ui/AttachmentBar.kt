package com.mateuszwozniak.chisel.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import com.mateuszwozniak.chisel.model.PromptAttachment
import java.awt.FlowLayout
import javax.swing.Icon
import javax.swing.JPanel

class AttachmentBar(private val project: Project) :
    JPanel(WrapLayout(FlowLayout.LEFT, JBUI.scale(GAP), JBUI.scale(GAP))) {

    private val attachments = mutableListOf<PromptAttachment>()

    init {
        isOpaque = false
        border = JBUI.Borders.emptyBottom(6)
        isVisible = false
    }

    fun all(): List<PromptAttachment> = attachments.toList()

    fun attach(attachment: PromptAttachment) {
        if (attachments.any { it.path == attachment.path }) return
        attachments.add(attachment)
        rebuild()
    }

    fun attachAll(items: List<PromptAttachment>) {
        items.forEach(::attach)
    }

    fun clear() {
        if (attachments.isEmpty()) return
        attachments.clear()
        rebuild()
    }

    private fun rebuild() {
        removeAll()
        attachments.forEach { attachment ->
            add(
                PromptChip(
                    label(attachment),
                    icon(attachment),
                    attachment.path,
                    { open(attachment) },
                ) {
                    attachments.remove(attachment)
                    rebuild()
                }
            )
        }
        isVisible = attachments.isNotEmpty()
        revalidate()
        repaint()
    }

    private fun icon(attachment: PromptAttachment): Icon? {
        if (attachment.isImage) return AllIcons.FileTypes.Image
        return FileTypeManager.getInstance().getFileTypeByFileName(attachment.name).icon
    }

    private fun open(attachment: PromptAttachment) {
        FileOpener.open(project, attachment.path)
    }

    private fun label(attachment: PromptAttachment): String {
        val name = attachment.name
        if (name.length <= NAME_LIMIT) return name
        val extension = name.substringAfterLast('.', "")
        val head = name.take((NAME_LIMIT - extension.length - 2).coerceAtLeast(1))
        return if (extension.isEmpty()) "$head…" else "$head….$extension"
    }

    private companion object {
        const val GAP = 6
        const val NAME_LIMIT = 22
    }
}
