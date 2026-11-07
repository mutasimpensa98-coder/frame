package com.frameglass.data.db

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Room refuses to open a database that differs from the schema it expects, and on the owner's phone that would be a
 * crash at every start. MigrationTestHelper needs a device, so this holds the statements against the exported schema.
 */
class MigrationSchemaTest {
    private val schemas = File("schemas/com.frameglass.data.db.FrameDb")

    private fun entities(version: Int): Map<String, JsonObject> {
        val database = Json.parseToJsonElement(File(schemas, "$version.json").readText()).jsonObject.getValue("database").jsonObject
        return database.getValue("entities").jsonArray.map { it.jsonObject }
            .associateBy { it.getValue("tableName").jsonPrimitive.content }
    }

    private fun JsonObject.createSql(table: String) = getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)

    private fun JsonObject.indexSql(table: String) =
        this["indices"]?.jsonArray.orEmpty().map { it.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table) }

    private fun columns(entity: JsonObject) = entity.getValue("fields").jsonArray.map { it.jsonObject.getValue("columnName").jsonPrimitive.content }

    @Test
    fun everyNewTableIsCreatedExactlyAsRoomExpects() {
        val old = entities(1)
        val new = entities(2)
        for ((name, entity) in new - old.keys) {
            assertTrue("no statement creates $name", entity.createSql(name) in Migrations.STATEMENTS_1_2)
            entity.indexSql(name).forEach { assertTrue("no statement creates $it", it in Migrations.STATEMENTS_1_2) }
        }
    }

    @Test
    fun theOnlyChangedTableIsTitleAndItGainsOneColumn() {
        val old = entities(1)
        val new = entities(2)
        val changed = old.keys.filter { old.getValue(it).createSql(it) != new.getValue(it).createSql(it) }
        assertEquals(listOf("Title"), changed)
        assertEquals(listOf("favorite"), columns(new.getValue("Title")) - columns(old.getValue("Title")).toSet())
        assertEquals(columns(old.getValue("Title")), columns(new.getValue("Title")) - "favorite")
    }

    @Test
    fun theNewColumnMatchesRoomsDefinition() {
        val field = entities(2).getValue("Title").getValue("fields").jsonArray.map { it.jsonObject }
            .single { it.getValue("columnName").jsonPrimitive.content == "favorite" }
        assertEquals("0", field.getValue("defaultValue").jsonPrimitive.content)
        assertTrue("ALTER TABLE `Title` ADD COLUMN `favorite` INTEGER NOT NULL DEFAULT 0" in Migrations.STATEMENTS_1_2)
    }

    @Test
    fun noStatementTouchesAnExistingRow() {
        Migrations.STATEMENTS_1_2.forEach { sql ->
            assertTrue(sql, sql.startsWith("CREATE ") || sql.startsWith("ALTER TABLE `Title` ADD COLUMN"))
        }
    }

    @Test
    fun theMigrationRunsFromOneToTwo() {
        assertEquals(1, Migrations.MIGRATION_1_2.startVersion)
        assertEquals(2, Migrations.MIGRATION_1_2.endVersion)
    }
}
