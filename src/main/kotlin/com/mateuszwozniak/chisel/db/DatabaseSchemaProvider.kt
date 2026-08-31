package com.mateuszwozniak.chisel.db

import com.intellij.database.model.DasTable
import com.intellij.database.psi.DbDataSource
import com.intellij.database.psi.DbPsiFacade
import com.intellij.database.util.DasUtil
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project

class DatabaseSchemaProvider : SchemaProvider {

    override fun catalog(project: Project): SchemaCatalog {
        val sources = DbPsiFacade.getInstance(project).dataSources
        if (sources.isEmpty()) return SchemaCatalog(emptyList(), NO_DATA_SOURCES)
        return SchemaCatalog(sources.flatMap { targets(it) }, null)
    }

    override fun render(project: Project, target: SchemaTarget): String? {
        val source = DbPsiFacade.getInstance(project).dataSources
            .firstOrNull { it.uniqueId == target.dataSourceId } ?: return null
        val tables = introspected(source).filter { target.schemaName == null || schemaOf(it) == target.schemaName }
        if (tables.isEmpty()) return null
        return ReadAction.compute<String, RuntimeException> {
            SchemaDdl(source.name, version(source), target.schemaName, tables).render()
        }
    }

    private fun targets(source: DbDataSource): List<SchemaTarget> {
        val tables = tables(source)
        if (tables.isEmpty()) return listOf(SchemaTarget(source.uniqueId, source.name, null, 0))
        return tables.groupBy { schemaOf(it) }
            .map { (schema, grouped) -> SchemaTarget(source.uniqueId, source.name, schema, grouped.size) }
            .sortedBy { it.schemaName.orEmpty() }
    }

    private fun introspected(source: DbDataSource): List<DasTable> {
        val loaded = tables(source)
        if (loaded.isNotEmpty()) return loaded
        if (!refresh(source)) return emptyList()
        return await(source)
    }

    private fun refresh(source: DbDataSource): Boolean = runCatching {
        val facade = DbPsiFacade.getInstance(source.project)
        val logic = facade.javaClass.classLoader.loadClass(REFRESH_LOGIC)
        val refresh = logic.methods.first {
            it.name == REFRESH_METHOD && it.parameterCount == 2
        }
        refresh.invoke(null, source.project, source.delegate)
        true
    }.getOrDefault(false)

    private fun await(source: DbDataSource): List<DasTable> {
        val deadline = System.currentTimeMillis() + LOAD_TIMEOUT
        while (System.currentTimeMillis() < deadline) {
            ProgressManager.checkCanceled()
            val tables = tables(source)
            if (tables.isNotEmpty()) return tables
            Thread.sleep(POLL_INTERVAL)
        }
        return emptyList()
    }

    private fun tables(source: DbDataSource): List<DasTable> =
        ReadAction.compute<List<DasTable>, RuntimeException> {
            DasUtil.getTables(source).toList().filter { !it.isSystem && !it.isTemporary }
        }

    private fun schemaOf(table: DasTable): String? =
        DasUtil.getSchema(table)?.takeIf { it.isNotBlank() }

    private fun version(source: DbDataSource): String? {
        val version = source.databaseVersion ?: return null
        return listOfNotNull(
            version.name?.takeIf { it.isNotBlank() },
            version.version?.takeIf { it.isNotBlank() },
        ).joinToString(" ").takeIf { it.isNotBlank() }
    }

    private companion object {
        const val LOAD_TIMEOUT = 120_000L
        const val POLL_INTERVAL = 250L
        const val REFRESH_LOGIC = "com.intellij.database.actions.RefreshActionsLogic"
        const val REFRESH_METHOD = "runDataSourceGeneralRefresh"
        const val NO_DATA_SOURCES =
            "No data sources are configured. Add one in the Database tool window first."
    }
}
