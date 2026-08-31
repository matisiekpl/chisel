package com.mateuszwozniak.chisel.service

import com.mateuszwozniak.chisel.model.TodoItem
import com.mateuszwozniak.chisel.model.TranscriptItem

interface ConversationListener {

    fun onItemAdded(item: TranscriptItem) = Unit

    fun onItemUpdated(item: TranscriptItem) = Unit

    fun onTranscriptReset() = Unit

    fun onTodosChanged(todos: List<TodoItem>) = Unit

    fun onBusyChanged(busy: Boolean) = Unit

    fun onTitleChanged(title: String) = Unit

    fun onOptionsChanged() = Unit
}
