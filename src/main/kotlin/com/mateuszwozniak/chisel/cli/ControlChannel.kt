package com.mateuszwozniak.chisel.cli

import com.google.gson.JsonObject
import com.mateuszwozniak.chisel.protocol.IncomingFrame
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class ControlChannel(
    private val codec: StreamJsonCodec,
    private val transport: (String) -> Unit,
) {

    private val counter = AtomicInteger()
    private val pending = ConcurrentHashMap<String, CompletableFuture<JsonObject?>>()

    fun request(subtype: String, fill: JsonObject.() -> Unit = {}): CompletableFuture<JsonObject?> {
        val requestId = "req_${counter.incrementAndGet()}_${Random.nextInt().toUInt().toString(16)}"
        val body = JsonObject().apply {
            addProperty("subtype", subtype)
            fill()
        }
        val answer = CompletableFuture<JsonObject?>()
        pending[requestId] = answer
        runCatching { transport(codec.controlRequest(requestId, body)) }
            .onFailure {
                pending.remove(requestId)
                answer.completeExceptionally(it)
            }
        return answer
    }

    fun complete(frame: IncomingFrame.ControlAnswer) {
        val answer = pending.remove(frame.requestId) ?: return
        if (frame.error != null) answer.completeExceptionally(IllegalStateException(frame.error))
        else answer.complete(frame.payload)
    }

    fun failAll(reason: String) {
        pending.keys.toList().forEach { requestId ->
            pending.remove(requestId)?.completeExceptionally(IllegalStateException(reason))
        }
    }
}
