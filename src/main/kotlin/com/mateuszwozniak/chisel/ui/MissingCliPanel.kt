package com.mateuszwozniak.chisel.ui

import com.intellij.ui.components.JBPanelWithEmptyText
import com.mateuszwozniak.chisel.cli.ClaudeExecutable

class MissingCliPanel : JBPanelWithEmptyText() {

    init {
        emptyText.text = ClaudeExecutable.MISSING_HEADLINE
        emptyText.appendLine(ClaudeExecutable.MISSING_DETAIL)
        emptyText.appendLine(ClaudeExecutable.MISSING_HINT)
    }
}
