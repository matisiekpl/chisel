package com.mateuszwozniak.chisel.db

import com.intellij.database.model.DasColumn
import com.intellij.database.model.DasForeignKey
import com.intellij.database.model.DasNamed
import com.intellij.database.model.DasTable
import com.intellij.database.model.ObjectKind
import com.intellij.database.util.DasUtil

class SchemaDdl(
    private val dataSourceName: String,
    private val databaseVersion: String?,
    private val schemaName: String?,
    private val tables: List<DasTable>,
) {

    fun render(): String {
        val ordered = tables.sortedBy { it.name }
        val shown = ordered.take(TABLE_LIMIT)
        return header() + shown.joinToString("\n\n") { table(it) } + footer()
    }

    private fun header(): String {
        val source = dataSourceName + (databaseVersion?.let { " ($it)" } ?: "")
        val schema = schemaName?.let { "-- Schema: " + it + "\n" } ?: ""
        return "-- Data source: " + source + "\n" + schema +
            "-- " + tables.size + " tables, exported from the IntelliJ data source model by Chisel.\n\n"
    }

    private fun footer(): String {
        if (tables.size <= TABLE_LIMIT) return "\n"
        return "\n\n-- " + tables.size + " tables in this schema; " + TABLE_LIMIT +
            " shown. Attach a narrower schema or ask the agent to query the database for the rest.\n"
    }

    private fun table(table: DasTable): String {
        val columns = DasUtil.getColumns(table).toList().sortedBy { it.position }
        val comment = table.comment?.takeIf { it.isNotBlank() }?.let { "-- " + it + "\n" } ?: ""
        if (isView(table)) {
            val names = columns.map { identifier(it) to null }
            return comment + "CREATE VIEW " + qualify(table) + " (\n" + body(names) + "\n);"
        }
        val lines = columns.map { column(it) } +
            listOfNotNull(primaryKey(table)).map { it to null } +
            foreignKeys(table).map { it to null }
        val create = comment + "CREATE TABLE " + qualify(table) + " (\n" + body(lines) + "\n);"
        val indices = indices(table)
        return if (indices.isEmpty()) create else create + "\n" + indices.joinToString("\n")
    }

    private fun body(lines: List<Pair<String, String?>>): String =
        lines.mapIndexed { index, line ->
            val separator = if (index < lines.size - 1) "," else ""
            val note = line.second?.let { "  -- " + it } ?: ""
            INDENT + line.first + separator + note
        }.joinToString("\n")

    private fun column(column: DasColumn): Pair<String, String?> {
        val notNull = if (column.isNotNull) " NOT NULL" else ""
        val default = column.default?.takeIf { it.isNotBlank() }?.let { " DEFAULT " + it } ?: ""
        val text = identifier(column) + " " + column.dataType.specification + notNull + default
        return text to column.comment?.takeIf { it.isNotBlank() }
    }

    private fun primaryKey(table: DasTable): String? {
        val columns = primaryKeyColumns(table)
        if (columns.isEmpty()) return null
        return "PRIMARY KEY (" + columns.joinToString(", ") + ")"
    }

    private fun primaryKeyColumns(table: DasTable): List<String> =
        DasUtil.getPrimaryKey(table)?.columnsRef?.names()?.toList().orEmpty()

    private fun foreignKeys(table: DasTable): List<String> =
        DasUtil.getForeignKeys(table).toList().map { foreignKey(it) }

    private fun foreignKey(key: DasForeignKey): String {
        val target = listOfNotNull(
            key.refTableSchema?.takeIf { it.isNotBlank() }?.let { quote(it, false) },
            quote(key.refTableName, false),
        ).joinToString(".")
        return "FOREIGN KEY (" + key.columnsRef.names().joinToString(", ") + ") REFERENCES " +
            target + " (" + key.refColumns.names().joinToString(", ") + ")" +
            rule("ON DELETE", key.deleteRule) + rule("ON UPDATE", key.updateRule)
    }

    private fun rule(keyword: String, action: DasForeignKey.RuleAction?): String {
        if (action == null || action == DasForeignKey.RuleAction.NO_ACTION) return ""
        return " " + keyword + " " + action.name.replace('_', ' ')
    }

    private fun indices(table: DasTable): List<String> {
        val primary = primaryKeyColumns(table)
        return DasUtil.getIndices(table).toList().mapNotNull { index ->
            val columns = index.columnsRef.names().toList()
            if (columns.isEmpty() || columns == primary) return@mapNotNull null
            val unique = if (index.isUnique) "UNIQUE " else ""
            "CREATE " + unique + "INDEX " + identifier(index) + " ON " + qualify(table) +
                " (" + columns.joinToString(", ") + ");"
        }
    }

    private fun isView(table: DasTable): Boolean =
        table.kind == ObjectKind.VIEW || table.kind == ObjectKind.MAT_VIEW

    private fun qualify(table: DasTable): String = listOfNotNull(
        schemaName?.let { quote(it, false) },
        identifier(table),
    ).joinToString(".")

    private fun identifier(named: DasNamed): String = quote(named.name, named.isQuoted)

    private fun quote(name: String, quoted: Boolean): String =
        if (quoted || !name.matches(PLAIN_NAME)) "\"" + name + "\"" else name

    private companion object {
        const val TABLE_LIMIT = 400
        const val INDENT = "    "
        val PLAIN_NAME = Regex("[A-Za-z0-9_]+")
    }
}
