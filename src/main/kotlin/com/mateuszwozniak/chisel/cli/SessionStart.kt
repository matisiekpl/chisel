package com.mateuszwozniak.chisel.cli

sealed interface SessionStart {

    val sessionId: String

    data class Fresh(override val sessionId: String) : SessionStart

    data class Resume(override val sessionId: String) : SessionStart

    data class ResumeAt(override val sessionId: String, val messageUuid: String) : SessionStart
}
