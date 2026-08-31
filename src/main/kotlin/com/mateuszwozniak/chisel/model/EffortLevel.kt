package com.mateuszwozniak.chisel.model

enum class EffortLevel(val value: String, val label: String, val ultracode: Boolean = false) {
    LOW("low", "Low"),
    MEDIUM("medium", "Medium"),
    HIGH("high", "High"),
    XHIGH("xhigh", "Extra high"),
    MAX("max", "Max"),
    ULTRACODE("xhigh", "Ultracode", true),
}
