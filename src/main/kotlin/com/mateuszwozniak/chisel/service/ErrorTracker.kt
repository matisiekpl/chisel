package com.mateuszwozniak.chisel.service

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import io.sentry.Sentry
import io.sentry.SentryLevel
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

@Service(Service.Level.APP)
class ErrorTracker : Disposable {

    private val handler = object : Handler() {

        override fun publish(record: LogRecord) {
            if (record.level.intValue() < Level.WARNING.intValue()) return
            val level = if (record.level == Level.SEVERE) SentryLevel.ERROR else SentryLevel.WARNING
            val ownLogger = record.loggerName.orEmpty().contains(PACKAGE)
            val thrown = record.thrown
            if (thrown == null) {
                if (ownLogger) Sentry.captureMessage(record.message.orEmpty(), level)
                return
            }
            val ownStack = generateSequence(thrown) { it.cause }
                .any { cause -> cause.stackTrace.any { it.className.startsWith(PACKAGE) } }
            if (!ownLogger && !ownStack) return
            Sentry.captureException(thrown) { scope ->
                scope.level = level
                scope.setExtra("message", record.message.orEmpty())
                scope.setTag("logger", record.loggerName.orEmpty())
            }
        }

        override fun flush() = Unit

        override fun close() = Unit
    }

    init {
        Sentry.init { options ->
            options.dsn = DSN
            options.release = "chisel@" + PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))?.version
            options.isEnableUncaughtExceptionHandler = false
            options.setTag("ide", ApplicationInfo.getInstance().build.asString())
        }
        Logger.getLogger("").addHandler(handler)
    }

    override fun dispose() {
        Logger.getLogger("").removeHandler(handler)
        Sentry.close()
    }

    companion object {
        fun getInstance(): ErrorTracker = service()

        private const val DSN =
            "https://a647de64cf3cc4e4ac26fd8ff9062aef@o337011.ingest.us.sentry.io/4512344253726720"

        private const val PLUGIN_ID = "com.mateuszwozniak.chisel"

        private const val PACKAGE = "com.mateuszwozniak.chisel"
    }
}
