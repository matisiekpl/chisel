package com.mateuszwozniak.chisel.model

class PlanTask(
    val id: String,
    val subject: String,
    val description: String,
) {

    var status: TodoStatus = TodoStatus.PENDING

    companion object {

        const val CREATE_TOOL = "TaskCreate"

        const val UPDATE_TOOL = "TaskUpdate"

        val TOOLS = setOf(CREATE_TOOL, UPDATE_TOOL, "TaskList", "TaskGet", "TaskOutput", "TaskStop")
    }
}
