package com.sigil.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Ciphertext for one secret value; the plaintext never touches disk. See [com.sigil.app.security.SecretVault]. */
@Entity(tableName = "secrets")
data class SecretEntity(
    @PrimaryKey val id: String,
    val ciphertext: ByteArray,
    val iv: ByteArray,
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val space: String,
    val pinned: Boolean,
    val updated: Long,
    val deletedAt: Long?,
    /** JSON-encoded List<NoteBlock> - see NoteBlockJson.kt. */
    @ColumnInfo(name = "blocks_json") val blocksJson: String,
)

@Entity(tableName = "logins")
data class LoginEntity(
    @PrimaryKey val id: String,
    val space: String,
    val pinned: Boolean,
    val updated: Long,
    val deletedAt: Long?,
    val title: String,
    val username: String,
    @ColumnInfo(name = "password_secret_id") val passwordSecretId: String,
    val url: String,
    @ColumnInfo(name = "is_root") val isRoot: Boolean,
    @ColumnInfo(name = "totp_secret_id") val totpSecretId: String?,
    @ColumnInfo(name = "has_passkey") val hasPasskey: Boolean,
    val leaked: Boolean,
    @ColumnInfo(name = "supports_2fa") val supports2FA: Boolean,
    @ColumnInfo(defaultValue = "LOGIN") val category: String = "LOGIN",
    /** JSON-encoded List<CustomField> - see FieldsJson. */
    @ColumnInfo(name = "fields_json", defaultValue = "[]") val fieldsJson: String = "[]",
    @ColumnInfo(defaultValue = "") val notes: String = "",
)

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val id: String,
    val space: String,
    val pinned: Boolean,
    val updated: Long,
    val deletedAt: Long?,
    val title: String,
    val holder: String,
    @ColumnInfo(name = "number_secret_id") val numberSecretId: String,
    val last4: String,
    val expiry: String,
    @ColumnInfo(name = "code_secret_id") val codeSecretId: String,
)

@Entity(tableName = "wifi_networks")
data class WifiEntity(
    @PrimaryKey val id: String,
    val space: String,
    val pinned: Boolean,
    val updated: Long,
    val deletedAt: Long?,
    val title: String,
    val network: String,
    @ColumnInfo(name = "password_secret_id") val passwordSecretId: String,
    val security: String,
)
