package com.mateuszwozniak.chisel.protocol

import com.google.gson.JsonObject

data class PermissionRequest(
    val requestId: String,
    val toolName: String,
    val toolUseId: String?,
    val input: JsonObject,
    val decisionReason: String?,
    val blockedPath: String?,
)
