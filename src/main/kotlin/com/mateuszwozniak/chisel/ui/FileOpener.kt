package com.mateuszwozniak.chisel.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem

object FileOpener {

    fun open(project: Project, path: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
                ?: return@executeOnPooledThread
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                FileEditorManager.getInstance(project).openFile(file, true)
            }
        }
    }
}
