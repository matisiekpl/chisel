package com.mateuszwozniak.chisel.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import java.nio.file.Path

object VfsRefresh {

    fun refreshFile(path: Path) {
        ApplicationManager.getApplication().executeOnPooledThread {
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
        }
    }

    fun refreshEverything() {
        VirtualFileManager.getInstance().asyncRefresh()
    }
}
