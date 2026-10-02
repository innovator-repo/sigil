package com.sigil.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SecretEntity::class, NoteEntity::class, LoginEntity::class, CardEntity::class, WifiEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class SigilDatabase : RoomDatabase() {
    abstract fun secretDao(): SecretDao
    abstract fun noteDao(): NoteDao
    abstract fun loginDao(): LoginDao
    abstract fun cardDao(): CardDao
    abstract fun wifiDao(): WifiDao

    companion object {
        @Volatile private var instance: SigilDatabase? = null

        fun get(context: Context): SigilDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SigilDatabase::class.java,
                "sigil.db",
            ).build().also { instance = it }
        }
    }
}
