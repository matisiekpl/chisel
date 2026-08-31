package com.mateuszwozniak.chisel.model

data class SessionOptions(
    val mode: AgentMode,
    val model: AgentModel,
    val effort: EffortLevel,
)
