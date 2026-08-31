package com.mateuszwozniak.chisel.model

import java.time.Instant

data class UsageLimit(
    val label: String,
    val percent: Int,
    val resetsAt: Instant?,
) {

    fun isExpired(now: Instant): Boolean = resetsAt != null && resetsAt.isBefore(now)
}
