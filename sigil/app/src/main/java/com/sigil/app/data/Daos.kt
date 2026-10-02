package com.sigil.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SecretDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(secret: SecretEntity)

    @Query("SELECT * FROM secrets WHERE id = :id")
    suspend fun get(id: String): SecretEntity?

    @Query("DELETE FROM secrets WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updated DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)
}

@Dao
interface LoginDao {
    @Query("SELECT * FROM logins ORDER BY updated DESC")
    fun observeAll(): Flow<List<LoginEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(login: LoginEntity)

    @Delete
    suspend fun delete(login: LoginEntity)
}

@Dao
interface CardDao {
    @Query("SELECT * FROM cards ORDER BY updated DESC")
    fun observeAll(): Flow<List<CardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(card: CardEntity)

    @Delete
    suspend fun delete(card: CardEntity)
}

@Dao
interface WifiDao {
    @Query("SELECT * FROM wifi_networks ORDER BY updated DESC")
    fun observeAll(): Flow<List<WifiEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(wifi: WifiEntity)

    @Delete
    suspend fun delete(wifi: WifiEntity)
}
