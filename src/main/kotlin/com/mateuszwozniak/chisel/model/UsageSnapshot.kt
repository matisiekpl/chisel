package com.mateuszwozniak.chisel.model

import java.time.Instant

data class UsageSnapshot(
    val limits: List<UsageLimit>,
    val measuredAt: Instant,
    val live: Boolean,
)
