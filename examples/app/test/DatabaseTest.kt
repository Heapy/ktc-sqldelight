package example

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import example.db.ExampleDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

class DatabaseTest {
    @Test
    fun generatedDatabaseCreatesSchemaAndExecutesTypedQueries() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            ExampleDatabase.Schema.create(driver)
            val database = ExampleDatabase(driver)
            database.personQueries.insertPerson(7, "Ada")
            val row = database.personQueries.selectAll().executeAsOne()
            assertEquals(7L, row.id)
            assertEquals("Ada", row.name)
        } finally {
            driver.close()
        }
    }
}
