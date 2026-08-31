package com.mateuszwozniak.chisel.model

data class TodoItem(val content: String, val status: TodoStatus)

enum class TodoStatus(val wireName: String) {
    PENDING("pending"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed");

    companion object {
        fun fromWireName(wireName: String?): TodoStatus =
            entries.firstOrNull { it.wireName == wireName } ?: PENDING
    }
}
