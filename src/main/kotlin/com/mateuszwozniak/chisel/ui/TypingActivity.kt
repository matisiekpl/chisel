package com.mateuszwozniak.chisel.ui

import javax.swing.Timer

object TypingActivity {

    private const val IDLE_DELAY = 1500

    @Volatile
    private var lastKeystroke = 0L

    fun record() {
        lastKeystroke = System.currentTimeMillis()
    }

    fun whenIdle(action: () -> Unit) {
        val remaining = IDLE_DELAY - (System.currentTimeMillis() - lastKeystroke)
        if (remaining <= 0) {
            action()
            return
        }
        Timer(remaining.toInt()) { whenIdle(action) }.apply { isRepeats = false }.start()
    }
}
