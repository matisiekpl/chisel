package com.mateuszwozniak.chisel.protocol

import com.google.gson.JsonObject

sealed interface IncomingFrame {

    data class Event(val event: StreamEvent) : IncomingFrame

    data class Permission(val request: PermissionRequest) : IncomingFrame

    data class ControlAnswer(
        val requestId: String,
        val payload: JsonObject?,
        val error: String?,
    ) : IncomingFrame

    data class ControlCancelled(val requestId: String) : IncomingFrame
}
