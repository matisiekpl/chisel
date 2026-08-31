package com.mateuszwozniak.chisel.protocol

import com.google.gson.JsonArray
import com.google.gson.JsonObject

internal fun JsonObject.string(name: String): String? =
    get(name)?.takeIf { it.isJsonPrimitive }?.asString

internal fun JsonObject.bool(name: String): Boolean =
    get(name)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false

internal fun JsonObject.number(name: String): Double? =
    get(name)?.takeIf { it.isJsonPrimitive }?.asDouble

internal fun JsonObject.obj(name: String): JsonObject? =
    get(name)?.takeIf { it.isJsonObject }?.asJsonObject

internal fun JsonObject.array(name: String): JsonArray? =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray
