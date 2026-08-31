package com.mateuszwozniak.chisel.model

data class QueuedPrompt(
    val text: String,
    val attachments: List<PromptAttachment>,
)
