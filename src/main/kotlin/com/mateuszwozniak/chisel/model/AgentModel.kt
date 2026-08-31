package com.mateuszwozniak.chisel.model

enum class AgentModel(val alias: String, val label: String) {
    BEST("best", "Best available"),
    OPUS("opus", "Opus"),
    SONNET("sonnet", "Sonnet"),
    HAIKU("haiku", "Haiku"),
    FABLE("fable", "Fable"),
}
