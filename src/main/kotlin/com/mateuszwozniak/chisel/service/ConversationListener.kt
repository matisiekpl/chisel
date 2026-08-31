package com.mateuszwozniak.chisel.service

import com.mateuszwozniak.chisel.model.QueuedPrompt
import com.mateuszwozniak.chisel.model.TodoItem
import com.mateuszwozniak.chisel.model.TranscriptItem

interface ConversationListener {

    fun onItemAdded(item: TranscriptItem) = Unit

    fun onItemUpdated(item: TranscriptItem) = Unit

    fun onTranscriptReset() = Unit

    fun onTodosChanged(todos: List<TodoItem>) = Unit

    fun onBusyChanged(busy: Boolean) = Unit

    fun onTitleChanged(title: String) = Unit

    fun onSessionStarted() = Unit

    fun onOptionsChanged() = Unit

    fun onQueueChanged(queued: List<QueuedPrompt>) = Unit

    fun onTasksChanged() = Unit

    fun onPlanTasksChanged() = Unit

    fun onContextChanged() = Unit

    fun onUsageChanged() = Unit

    fun onRemoteChanged() = Unit

    fun onCompactingChanged(running: Boolean) = Unit
}
