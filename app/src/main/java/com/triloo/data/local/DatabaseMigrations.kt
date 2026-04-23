package com.triloo.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Явные миграции Room для сохранения пользовательских данных между версиями схемы.
 */
object DatabaseMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                ALTER TABLE participants
                ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0
                """.trimIndent()
            )
            database.execSQL(
                """
                ALTER TABLE participants
                ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0
                """.trimIndent()
            )
            database.execSQL(
                """
                UPDATE participants
                SET createdAt = joinedAt,
                    updatedAt = COALESCE(lastLocationUpdate, joinedAt)
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS deletion_log (
                    id TEXT NOT NULL PRIMARY KEY,
                    tripId TEXT NOT NULL,
                    entityType TEXT NOT NULL,
                    entityId TEXT NOT NULL,
                    deletedAt INTEGER NOT NULL,
                    deviceId TEXT
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE trips ADD COLUMN destinationLatitude REAL"
            )
            database.execSQL(
                "ALTER TABLE trips ADD COLUMN destinationLongitude REAL"
            )
        }
    }
}
