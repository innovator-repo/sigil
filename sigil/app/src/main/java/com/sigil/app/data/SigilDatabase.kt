package com.sigil.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SecretEntity::class, NoteEntity::class, LoginEntity::class, CardEntity::class, WifiEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class SigilDatabase : RoomDatabase() {
    abstract fun secretDao(): SecretDao
    abstract fun noteDao(): NoteDao
    abstract fun loginDao(): LoginDao
    abstract fun cardDao(): CardDao
    abstract fun wifiDao(): WifiDao

    companion object {
        /** v2: password items gain a kind (bank, lock…), extra fields and notes. Existing items become Logins. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE logins ADD COLUMN category TEXT NOT NULL DEFAULT 'LOGIN'")
                db.execSQL("ALTER TABLE logins ADD COLUMN fields_json TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE logins ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile private var instance: SigilDatabase? = null

        fun get(context: Context): SigilDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SigilDatabase::class.java,
                "sigil.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
