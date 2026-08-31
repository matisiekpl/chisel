package com.mateuszwozniak.chisel.util

object ProjectPaths {

    fun relative(basePath: String?, path: String): String =
        if (basePath != null && path.startsWith(basePath)) path.removePrefix(basePath).trimStart('/')
        else path
}
