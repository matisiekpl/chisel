package com.mateuszwozniak.chisel.cli

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Key
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

class ClaudeProcess(
    commandLine: GeneralCommandLine,
    private val onLine: (String) -> Unit,
    private val onStandardError: (String) -> Unit,
    private val onTerminated: (Int) -> Unit,
) : Disposable {

    private val handler = KillableProcessHandler(commandLine).apply {
        setShouldDestroyProcessRecursively(true)
    }

    private val readLock = Any()
    private val writeLock = Any()
    private val lineBuffer = StringBuilder()

    private val writer: OutputStreamWriter by lazy {
        OutputStreamWriter(handler.processInput, StandardCharsets.UTF_8)
    }

    fun start() {
        handler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                if (outputType === ProcessOutputTypes.STDERR) onStandardError(event.text)
                else consume(event.text)
            }

            override fun processTerminated(event: ProcessEvent) {
                onTerminated(event.exitCode)
            }
        })
        handler.startNotify()
    }

    fun send(line: String) {
        synchronized(writeLock) {
            writer.write(line)
            writer.write("\n")
            writer.flush()
        }
    }

    fun isRunning(): Boolean = !handler.isProcessTerminated

    override fun dispose() {
        handler.destroyProcess()
    }

    private fun consume(text: String) {
        val lines = mutableListOf<String>()
        synchronized(readLock) {
            lineBuffer.append(text)
            while (true) {
                val end = lineBuffer.indexOf("\n")
                if (end < 0) break
                lines.add(lineBuffer.substring(0, end).trim())
                lineBuffer.delete(0, end + 1)
            }
        }
        lines.filter { it.isNotEmpty() }.forEach(onLine)
    }
}
