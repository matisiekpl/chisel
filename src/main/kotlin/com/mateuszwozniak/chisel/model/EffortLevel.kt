package com.mateuszwozniak.chisel.model

enum class EffortLevel(val value: String, val label: String) {
    LOW("low", "Low"),
    MEDIUM("medium", "Medium"),
    HIGH("high", "High"),
    XHIGH("xhigh", "Extra high"),
    MAX("max", "Max"),
}
