package com.mateuszwozniak.chisel.model

class AgentTask(
    val id: String,
    val toolUseId: String?,
    val description: String,
    val subagentType: String?,
    val backgrounded: Boolean,
    val turn: Int,
) {

    var status: String = RUNNING

    var outputFile: String? = null

    val finished: Boolean get() = status != RUNNING

    companion object {
        const val RUNNING = "running"
    }
}
