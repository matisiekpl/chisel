package com.mateuszwozniak.chisel.ui

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.popup.PopupState
import com.mateuszwozniak.chisel.db.SchemaCatalog
import com.mateuszwozniak.chisel.db.SchemaProvider
import com.mateuszwozniak.chisel.db.SchemaTarget
import com.mateuszwozniak.chisel.model.PromptAttachment
import com.mateuszwozniak.chisel.util.ChiselPaths
import java.nio.file.Files
import javax.swing.JComponent
import javax.swing.ListSelectionModel

class SchemaAttachment(
    private val project: Project,
    private val onAttach: (PromptAttachment) -> Unit,
) {

    private val popupState = PopupState.forPopup()

    val available: Boolean get() = SchemaProvider.instance() != null

    fun choose(anchor: JComponent) {
        if (popupState.isRecentlyHidden) return
        val provider = SchemaProvider.instance() ?: return
        val catalog = ProgressManager.getInstance().runProcessWithProgressSynchronously(
            ThrowableComputable<SchemaCatalog, RuntimeException> {
                ReadAction.compute<SchemaCatalog, RuntimeException> { provider.catalog(project) }
            },
            "Reading Database Schemas",
            true,
            project,
        )
        if (catalog.targets.isEmpty()) {
            Messages.showInfoMessage(project, catalog.message ?: NOTHING_TO_ATTACH, TITLE)
            return
        }
        showPopup(provider, catalog.targets, anchor)
    }

    private fun showPopup(provider: SchemaProvider, targets: List<SchemaTarget>, anchor: JComponent) {
        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(targets)
            .setRenderer(SimpleListCellRenderer.create("") { target: SchemaTarget -> target.label })
            .setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
            .setTitle(TITLE)
            .setItemsChosenCallback { chosen -> attach(provider, chosen.toList()) }
            .createPopup()
        popupState.prepareToShow(popup)
        popup.showUnderneathOf(anchor)
    }

    private fun attach(provider: SchemaProvider, targets: List<SchemaTarget>) {
        if (targets.isEmpty()) return
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, TASK_TITLE, true) {

            private val attached = mutableListOf<PromptAttachment>()

            private val failed = mutableListOf<String>()

            override fun run(indicator: ProgressIndicator) {
                targets.forEach { target ->
                    indicator.checkCanceled()
                    indicator.text = target.label
                    val text = provider.render(project, target)
                    val attachment = text?.let { write(target, it) }
                    if (attachment != null) attached.add(attachment) else failed.add(target.label)
                }
            }

            override fun onSuccess() {
                attached.forEach(onAttach)
                if (failed.isEmpty()) return
                Messages.showWarningDialog(
                    project,
                    "No schema could be read for " + failed.joinToString(", ") +
                        ". Check the connection in the Database tool window.",
                    TITLE,
                )
            }
        })
    }

    private fun write(target: SchemaTarget, text: String): PromptAttachment? = runCatching {
        val file = ChiselPaths.schemas()?.resolve(target.fileName) ?: return null
        Files.writeString(file, text)
        PromptAttachment.of(file)
    }.getOrNull()

    private companion object {
        const val TITLE = "Attach Database Schema"
        const val TASK_TITLE = "Reading database schema"
        const val NOTHING_TO_ATTACH = "No database schema is available to attach."
    }
}
