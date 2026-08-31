package com.mateuszwozniak.chisel.protocol

import com.google.gson.JsonObject

sealed interface PermissionDecision {

    fun toJson(): JsonObject

    data class Allow(val updatedInput: JsonObject? = null) : PermissionDecision {
        override fun toJson(): JsonObject = JsonObject().apply {
            addProperty("behavior", "allow")
            updatedInput?.let { add("updatedInput", it) }
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
