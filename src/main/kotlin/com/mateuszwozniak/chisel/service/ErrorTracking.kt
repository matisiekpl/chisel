package com.mateuszwozniak.chisel.service

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class ErrorTracking : ProjectActivity {

    override suspend fun execute(project: Project) {
        ErrorTracker.getInstance()
    }
}
