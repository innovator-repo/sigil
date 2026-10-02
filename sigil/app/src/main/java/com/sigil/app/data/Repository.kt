package com.sigil.app.data

import com.sigil.app.data.model.CardItem
import com.sigil.app.data.model.Item
import com.sigil.app.data.model.LoginItem
import com.sigil.app.data.model.NoteBlock
import com.sigil.app.data.model.NoteItem
import com.sigil.app.data.model.Segment
import com.sigil.app.data.model.Space
import com.sigil.app.data.model.WifiItem
import com.sigil.app.security.SecretVault
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull

/**
 * The single source of truth for everything Sigil shows: merges the four item tables into
 * one [Item] stream, and is the only place that talks to [SecretVault], so secret values
 * never leak into a ViewModel or Composable except for the instant they're revealed.
 */
class Repository(
    private val db: SigilDatabase,
    private val vault: SecretVault,
) {
    fun observeItems(): Flow<List<Item>> = combine(
        db.noteDao().observeAll(),
        db.loginDao().observeAll(),
        db.cardDao().observeAll(),
        db.wifiDao().observeAll(),
    ) { notes, logins, cards, wifi ->
        buildList {
            notes.forEach { add(it.toDomain()) }
            logins.forEach { add(it.toDomain()) }
            cards.forEach { add(it.toDomain()) }
            wifi.forEach { add(it.toDomain()) }
        }
    }

    // ───────── Secrets ─────────

    suspend fun sealValue(plaintext: String): String {
        val id = "s-" + UUID.randomUUID().toString()
        val sealed = vault.seal(plaintext)
        db.secretDao().upsert(SecretEntity(id, sealed.ciphertext, sealed.iv))
        return id
    }

    suspend fun revealValue(secretId: String): String {
        val entity = db.secretDao().get(secretId) ?: return ""
        return vault.open(SealedSecret(entity.ciphertext, entity.iv))
    }

    /** Re-seals [plaintext] under a brand-new id and drops the old one, so stale ciphertext never lingers. */
    suspend fun rotateValue(oldSecretId: String, plaintext: String): String {
        val newId = sealValue(plaintext)
        db.secretDao().delete(oldSecretId)
        return newId
    }

    // ───────── Notes ─────────

    suspend fun saveNote(note: NoteItem) = db.noteDao().upsert(note.toEntity())

    suspend fun createNote(space: Space, seedText: String? = null): NoteItem {
        val blocks = if (seedText.isNullOrBlank()) {
            listOf(NoteBlock(id = newBlockId()))
        } else {
            listOf(NoteBlock(id = newBlockId(), segments = listOf(Segment.Text(seedText))), NoteBlock(id = newBlockId()))
        }
        val note = NoteItem(id = newId("n"), space = space, updated = System.currentTimeMillis(), blocks = blocks)
        db.noteDao().upsert(note.toEntity())
        return note
    }

    suspend fun deleteNoteForever(note: NoteItem) = db.noteDao().delete(note.toEntity())

    // ───────── Logins ─────────

    suspend fun createLogin(space: Space, title: String, username: String, url: String, passwordPlaintext: String): LoginItem {
        val pwId = sealValue(passwordPlaintext)
        val login = LoginItem(
            id = newId("l"), space = space, updated = System.currentTimeMillis(),
            title = title, username = username, passwordSecretId = pwId, url = url,
        )
        db.loginDao().upsert(login.toEntity())
        return login
    }

    suspend fun saveLogin(login: LoginItem) = db.loginDao().upsert(login.toEntity())

    suspend fun updateLoginPassword(login: LoginItem, newPlaintext: String): LoginItem {
        val newId = rotateValue(login.passwordSecretId, newPlaintext)
        val updated = login.copy(passwordSecretId = newId, leaked = false, updated = System.currentTimeMillis())
        db.loginDao().upsert(updated.toEntity())
        return updated
    }

    suspend fun turnOn2FA(login: LoginItem, totpSeedBase32: String): LoginItem {
        val id = sealValue(totpSeedBase32)
        val updated = login.copy(totpSecretId = id, updated = System.currentTimeMillis())
        db.loginDao().upsert(updated.toEntity())
        return updated
    }

    // ───────── Cards / Wi-Fi ─────────

    suspend fun saveCard(card: CardItem) = db.cardDao().upsert(card.toEntity())
    suspend fun saveWifi(wifi: WifiItem) = db.wifiDao().upsert(wifi.toEntity())

    // ───────── Shared item actions ─────────

    suspend fun setPinned(item: Item, pinned: Boolean) = saveAny(withPinned(item, pinned))
    suspend fun softDelete(item: Item) = saveAny(withDeletedAt(item, System.currentTimeMillis()))
    suspend fun restore(item: Item) = saveAny(withDeletedAt(item, null))

    private suspend fun saveAny(item: Item) {
        when (item) {
            is NoteItem -> db.noteDao().upsert(item.toEntity())
            is LoginItem -> db.loginDao().upsert(item.toEntity())
            is CardItem -> db.cardDao().upsert(item.toEntity())
            is WifiItem -> db.wifiDao().upsert(item.toEntity())
        }
    }

    private fun withPinned(item: Item, pinned: Boolean): Item = when (item) {
        is NoteItem -> item.copy(pinned = pinned)
        is LoginItem -> item.copy(pinned = pinned)
        is CardItem -> item.copy(pinned = pinned)
        is WifiItem -> item.copy(pinned = pinned)
    }

    private fun withDeletedAt(item: Item, at: Long?): Item = when (item) {
        is NoteItem -> item.copy(deletedAt = at)
        is LoginItem -> item.copy(deletedAt = at)
        is CardItem -> item.copy(deletedAt = at)
        is WifiItem -> item.copy(deletedAt = at)
    }

    suspend fun seedIfEmpty() {
        if (db.noteDao().observeAll().firstValueOrEmpty().isNotEmpty()) return
        SampleData.seed(this)
    }

    companion object {
        fun newId(prefix: String): String = "$prefix-" + UUID.randomUUID().toString().take(8)
        fun newBlockId(): String = "b-" + UUID.randomUUID().toString().take(8)
    }
}

// Entity <-> domain mapping

private fun NoteEntity.toDomain() = NoteItem(
    id = id, space = Space.valueOf(space), pinned = pinned, updated = updated, deletedAt = deletedAt,
    blocks = NoteBlockJson.decode(blocksJson),
)
private fun NoteItem.toEntity() = NoteEntity(
    id = id, space = space.name, pinned = pinned, updated = updated, deletedAt = deletedAt,
    blocksJson = NoteBlockJson.encode(blocks),
)

private fun LoginEntity.toDomain() = LoginItem(
    id = id, space = Space.valueOf(space), pinned = pinned, updated = updated, deletedAt = deletedAt,
    title = title, username = username, passwordSecretId = passwordSecretId, url = url,
    isRoot = isRoot, totpSecretId = totpSecretId, hasPasskey = hasPasskey, leaked = leaked, supports2FA = supports2FA,
)
private fun LoginItem.toEntity() = LoginEntity(
    id = id, space = space.name, pinned = pinned, updated = updated, deletedAt = deletedAt,
    title = title, username = username, passwordSecretId = passwordSecretId, url = url,
    isRoot = isRoot, totpSecretId = totpSecretId, hasPasskey = hasPasskey, leaked = leaked, supports2FA = supports2FA,
)

private fun CardEntity.toDomain() = CardItem(
    id = id, space = Space.valueOf(space), pinned = pinned, updated = updated, deletedAt = deletedAt,
    title = title, holder = holder, numberSecretId = numberSecretId, last4 = last4, expiry = expiry, codeSecretId = codeSecretId,
)
private fun CardItem.toEntity() = CardEntity(
    id = id, space = space.name, pinned = pinned, updated = updated, deletedAt = deletedAt,
    title = title, holder = holder, numberSecretId = numberSecretId, last4 = last4, expiry = expiry, codeSecretId = codeSecretId,
)

private fun WifiEntity.toDomain() = WifiItem(
    id = id, space = Space.valueOf(space), pinned = pinned, updated = updated, deletedAt = deletedAt,
    title = title, network = network, passwordSecretId = passwordSecretId, security = security,
)
private fun WifiItem.toEntity() = WifiEntity(
    id = id, space = space.name, pinned = pinned, updated = updated, deletedAt = deletedAt,
    title = title, network = network, passwordSecretId = passwordSecretId, security = security,
)

private suspend fun <T> Flow<List<T>>.firstValueOrEmpty(): List<T> = firstOrNull() ?: emptyList()
