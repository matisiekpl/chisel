package com.mateuszwozniak.chisel.model

data class ContextUsage(
    val percentage: Int,
    val usedTokens: Long,
    val maxTokens: Long,
    val categories: List<Category>,
) {

    data class Category(val name: String, val tokens: Long)
}
