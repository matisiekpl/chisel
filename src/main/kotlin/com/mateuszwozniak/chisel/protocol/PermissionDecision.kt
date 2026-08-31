package com.mateuszwozniak.chisel.protocol

import com.google.gson.JsonObject

sealed interface PermissionDecision {

    fun toJson(): JsonObject

    data object Allow : PermissionDecision {
        override fun toJson(): JsonObject = JsonObject().apply {
            addProperty("behavior", "allow")
        }
    }

    data class Deny(val message: String, val interrupt: Boolean = false) : PermissionDecision {
        override fun toJson(): JsonObject = JsonObject().apply {
            addProperty("behavior", "deny")
            addProperty("message", message)
            if (interrupt) addProperty("interrupt", true)
        }
    }
}
