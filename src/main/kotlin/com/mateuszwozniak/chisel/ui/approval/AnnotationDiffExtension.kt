package com.mateuszwozniak.chisel.ui.approval

import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.simple.SimpleOnesideDiffViewer
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.openapi.editor.ex.EditorEx

class AnnotationDiffExtension : DiffExtension() {

    override fun onViewerCreated(
        viewer: FrameDiffTool.DiffViewer,
        context: DiffContext,
        request: DiffRequest,
    ) {
        if (request.getUserData(DiffUserDataKeys.PLACE) != AnnotationKeys.DIFF_PLACE) return
        val model = request.getUserData(AnnotationKeys.MODEL) ?: return
        val editor = proposedEditor(viewer) ?: return
        AnnotationInlays(editor, model, viewer).install()
        UserEditHighlighter(editor, viewer).install()
    }

    private fun proposedEditor(viewer: FrameDiffTool.DiffViewer): EditorEx? = when (viewer) {
        is TwosideTextDiffViewer -> viewer.editor2
        is UnifiedDiffViewer -> viewer.editor
        is SimpleOnesideDiffViewer -> viewer.editor
        else -> null
    }
}
