package com.mateuszwozniak.chisel.cli

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.util.io.HttpRequests
import com.mateuszwozniak.chisel.model.UsageLimit
import com.mateuszwozniak.chisel.model.UsageSnapshot
import com.mateuszwozniak.chisel.protocol.number
import com.mateuszwozniak.chisel.protocol.obj
import com.mateuszwozniak.chisel.protocol.string
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

object ClaudeUsage {

    @Volatile
    private var attemptedAt: Instant? = null

    @Volatile
    private var cached: UsageSnapshot? = null

    fun snapshot(force: Boolean): UsageSnapshot? {
        if (!force && isFresh()) return cached
        val snapshot = fetch()?.let { UsageSnapshot(it, Instant.now(), true) } ?: fromConfiguration()
        attemptedAt = Instant.now()
        cached = snapshot
        return snapshot
    }

    private fun isFresh(): Boolean {
        val attempt = attemptedAt ?: return false
        return Duration.between(attempt, Instant.now()) < TTL
    }

    private fun fromConfiguration(): UsageSnapshot? {
        val cache = ClaudeConfiguration.read()?.obj("cachedUsageUtilization") ?: return null
        val measuredAt = cache.number("fetchedAtMs")?.toLong()?.let { Instant.ofEpochMilli(it) } ?: return null
        val limits = cache.obj("utilization")?.let { parse(it) } ?: return null
        return UsageSnapshot(limits, measuredAt, false)
    }

    private fun fetch(): List<UsageLimit>? {
        val token = ClaudeCredentials.accessToken() ?: return null
        val body = runCatching {
            HttpRequests.request(USAGE_URL)
                .accept(CONTENT_TYPE)
                .connectTimeout(TIMEOUT)
                .readTimeout(TIMEOUT)
                .tuner { connection ->
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    connection.setRequestProperty("Content-Type", CONTENT_TYPE)
                    connection.setRequestProperty("anthropic-beta", OAUTH_BETA)
                }
                .readString()
        }.getOrNull() ?: return null
        val utilization = runCatching { JsonParser.parseString(body) as? JsonObject }.getOrNull() ?: return null
        return parse(utilization).takeIf { it.isNotEmpty() }
    }

    private fun parse(utilization: JsonObject): List<UsageLimit> {
        val limits = utilization.get("limits")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return limits.mapNotNull { it as? JsonObject }.map { limit ->
            UsageLimit(
                label = label(limit),
                percent = limit.number("percent")?.toInt() ?: 0,
                resetsAt = instant(limit.string("resets_at")),
            )
        }
    }

    private fun instant(value: String?): Instant? =
        runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()

    private fun label(limit: JsonObject): String {
        val model = limit.obj("scope")?.obj("model")?.string("display_name")
        return when (val kind = limit.string("kind")) {
            "session" -> "Session, 5 hours"
            "weekly_all" -> "Weekly, all models"
            "weekly_scoped" -> "Weekly, " + (model ?: "scoped")
            else -> kind.orEmpty()
        }
    }

    private const val USAGE_URL = "https://api.anthropic.com/api/oauth/usage"

    private const val OAUTH_BETA = "oauth-2025-04-20"

    private const val CONTENT_TYPE = "application/json"

    private const val TIMEOUT = 5_000

    private val TTL: Duration = Duration.ofMinutes(5)
}
