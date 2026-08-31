package com.mateuszwozniak.chisel.db

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

data class SchemaTarget(
    val dataSourceId: String,
    val dataSourceName: String,
    val schemaName: String?,
    val tableCount: Int,
) {

    val label: String
        get() = listOfNotNull(
            dataSourceName,
            schemaName,
            if (tableCount > 0) tableCount.toString() + " tables" else "not introspected",
        ).joinToString(" · ")

    val fileName: String
        get() = sanitise(dataSourceName) + "-" + sanitise(schemaName ?: "all") + "-" +
            Integer.toHexString(dataSourceId.hashCode()) + ".sql"

    private fun sanitise(value: String): String = value
        .lowercase()
        .replace(SEPARATOR, "-")
        .take(NAME_LIMIT)
        .trim('-')
        .ifEmpty { "schema" }

    private companion object {
        const val NAME_LIMIT = 40
        val SEPARATOR = Regex("[^a-z0-9]+")
    }
}

data class SchemaCatalog(val targets: List<SchemaTarget>, val message: String?)

interface SchemaProvider {

    fun catalog(project: Project): SchemaCatalog

    fun render(project: Project, target: SchemaTarget): String?

    companion object {

        fun instance(): SchemaProvider? =
            ApplicationManager.getApplication().getService(SchemaProvider::class.java)
    }
}
