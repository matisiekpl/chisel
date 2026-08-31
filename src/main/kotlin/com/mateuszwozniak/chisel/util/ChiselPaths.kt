package com.mateuszwozniak.chisel.util

import com.intellij.openapi.application.PathManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object ChiselPaths {

    val root: Path get() = Paths.get(PathManager.getSystemPath(), "chisel")

    fun ensureRoot(): Path? = ensure(root)

    fun images(): Path? = ensure(root.resolve("images"))

    fun schemas(): Path? = ensure(root.resolve("schemas"))

    private fun ensure(path: Path): Path? = runCatching { Files.createDirectories(path) }.getOrNull()
}
