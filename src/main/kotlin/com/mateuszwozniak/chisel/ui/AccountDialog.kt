package com.mateuszwozniak.chisel.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.InplaceButton
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.mateuszwozniak.chisel.cli.ClaudeUsage
import com.mateuszwozniak.chisel.model.AccountProfile
import com.mateuszwozniak.chisel.model.UsageSnapshot
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionListener
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JProgressBar

class AccountDialog(
    project: Project,
    private val profile: AccountProfile?,
) : DialogWrapper(project) {

    private val usageHost = JPanel(BorderLayout())

    private val refreshButton = InplaceButton(
        "Refresh",
        AllIcons.Actions.Refresh,
        ActionListener { load(true) },
    )

    init {
        title = "Claude Account"
        refreshButton.preferredSize = Dimension(JBUI.scale(BUTTON_SIZE), JBUI.scale(BUTTON_SIZE))
        init()
        load(false)
    }

    override fun createActions(): Array<Action> = arrayOf(okAction)

    override fun createCenterPanel(): JComponent = panel {
        val account = profile
        if (account == null) {
            row { label("No signed-in account found. Run claude in a terminal and sign in.") }
            return@panel
        }
        account.fullName?.let { value -> row("Name:") { label(value) } }
        account.email?.let { value -> row("Email:") { label(value) } }
        account.organizationName?.let { value -> row("Organization:") { label(value) } }
        account.organizationRole?.let { value -> row("Role:") { label(readable(value)) } }
        account.plan?.let { value -> row("Plan:") { label(readable(value)) } }
        account.billing?.let { value -> row("Billing:") { label(readable(value)) } }
        row { cell(usageHost) }
    }

    private fun load(force: Boolean) {
        replaceUsage(panel { group(USAGE_TITLE) { row { label("Loading…") } } })
        ApplicationManager.getApplication().executeOnPooledThread {
            val snapshot = ClaudeUsage.snapshot(force)
            ApplicationManager.getApplication().invokeLater(
                { if (!isDisposed) replaceUsage(usageContent(snapshot)) },
                ModalityState.stateForComponent(usageHost),
            )
        }
    }

    private fun replaceUsage(content: JComponent) {
        usageHost.removeAll()
        usageHost.add(content, BorderLayout.CENTER)
        usageHost.revalidate()
        usageHost.repaint()
        pack()
    }

    private fun usageContent(snapshot: UsageSnapshot?): JComponent = panel {
        group(USAGE_TITLE) {
            if (snapshot == null) {
                row {
                    label("Usage is unavailable.")
                    cell(refreshButton)
                }
                return@group
            }
            val now = Instant.now()
            snapshot.limits.filterNot { it.isExpired(now) }.forEach { limit ->
                row(limit.label + ":") {
                    cell(meter(limit.percent))
                    label(limit.percent.toString() + "%")
                    limit.resetsAt?.let { comment("Resets " + timestamp(it)) }
                }
            }
            row {
                comment(freshness(snapshot, now))
                cell(refreshButton)
            }
        }
    }

    private fun freshness(snapshot: UsageSnapshot, now: Instant): String {
        if (snapshot.live) return "Fetched from your account at " + timestamp(snapshot.measuredAt) + "."
        val measured = "The CLI measured this at " + timestamp(snapshot.measuredAt) + "."
        if (Duration.between(snapshot.measuredAt, now) < STALE_AFTER) return measured
        return measured + " Run claude in a terminal to refresh it."
    }

    private fun meter(percent: Int): JProgressBar = JProgressBar(0, 100).apply {
        value = percent
        preferredSize = Dimension(JBUI.scale(METER_WIDTH), preferredSize.height)
    }

    private fun timestamp(instant: Instant): String =
        FORMATTER.format(instant.atZone(ZoneId.systemDefault()))

    private fun readable(value: String): String =
        value.split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

    private companion object {
        const val USAGE_TITLE = "Usage"
        const val METER_WIDTH = 200
        const val BUTTON_SIZE = 24
        val STALE_AFTER: Duration = Duration.ofHours(1)
        val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm")
    }
}
