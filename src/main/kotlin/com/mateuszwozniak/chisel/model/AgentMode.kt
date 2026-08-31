package com.mateuszwozniak.chisel.model

enum class AgentMode(val permissionMode: String, val label: String) {
    PLAN("plan", "Plan"),
    IMPLEMENTATION("manual", "Implementation");

    companion object {
        fun fromPermissionMode(permissionMode: String): AgentMode =
            entries.firstOrNull { it.permissionMode == permissionMode } ?: IMPLEMENTATION
    }
}
