package com.mateuszwozniak.chisel.model

enum class AgentMode(val permissionMode: String, val label: String) {
    ASK("manual", "Ask"),
    PLAN("plan", "Plan"),
    IMPLEMENTATION("manual", "Implementation"),
    AUTO("auto", "Auto");

    companion object {
        fun fromPermissionMode(permissionMode: String): AgentMode =
            if (permissionMode == PLAN.permissionMode) PLAN else IMPLEMENTATION
    }
}
