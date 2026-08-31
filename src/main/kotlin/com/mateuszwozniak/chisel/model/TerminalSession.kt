package com.mateuszwozniak.chisel.model

import java.time.Instant

data class TerminalSession(
    val id: String,
    val title: String,
    val modifiedAt: Instant,
)
