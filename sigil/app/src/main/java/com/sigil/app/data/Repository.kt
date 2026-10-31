package com.sigil.app.data

import com.sigil.app.data.model.CardItem
import com.sigil.app.data.model.Category
import com.sigil.app.data.model.CustomField
import com.sigil.app.data.model.ItemDraft
import com.sigil.app.data.model.Item
import com.sigil.app.data.model.LoginItem
import com.sigil.app.data.model.NoteItem
import com.sigil.app.data.model.Segment
import com.sigil.app.data.model.Space
import com.sigil.app.data.model.WifiItem
import com.sigil.app.security.SealedSecret
import com.sigil.app.security.SecretVault
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

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

    /** Every stored item once, without watching for changes: for the status provider and widgets. */
    suspend fun allItems(): List<Item> = buildList {
        db.noteDao().all().forEach { add(it.toDomain()) }
        db.loginDao().all().forEach { add(it.toDomain()) }
        db.cardDao().all().forEach { add(it.toDomain()) }
        db.wifiDao().all().forEach { add(it.toDomain()) }
    }

    // ───────── Secrets ─────────

    suspend fun sealValue(plaintext: String): String {
        val id = "s-" + UUID.randomUUID().toString()
        val sealed = vault.seal(plaintext)
        db.secretDao().upsert(SecretEntity(id, sealed.ciphertext, sealed.iv))
        return id
    }

    /** Length of a secret without decrypting it (AES-GCM adds a 16-byte tag), for sizing the mask. */
    suspend fun secretLength(secretId: String): Int =
        db.secretDao().get(secretId)?.let { it.ciphertext.size - 16 } ?: 0

    suspend fun revealValue(secretId: String): String {
        val entity = db.secretDao().get(secretId) ?: return ""
        return vault.open(SealedSecret(entity.ciphertext, entity.iv))
    }

    // ───────── Notes ─────────

    suspend fun saveNote(note: NoteItem) = db.noteDao().upsert(note.toEntity())


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

    /**
     * Saves what the editor produced, as a new item ([base] null) or over [base]. Every sealed
     * value is sealed afresh; ciphertext the item no longer uses is left for collectGarbage().
     */
    suspend fun saveDraft(base: Item?, d: ItemDraft, passwordChanged: Boolean): Item {
        val now = System.currentTimeMillis()
        suspend fun sealOrNone(v: String) = if (v.isEmpty()) "" else sealValue(v)
        val item: Item = when (d) {
            is ItemDraft.Login -> {
                val fields = d.fields.filter { it.label.isNotBlank() || it.value.isNotBlank() }.map { f ->
                    if (f.secret) CustomField(f.id, f.label.trim(), secretId = sealValue(f.value))
                    else CustomField(f.id, f.label.trim(), value = f.value.trim())
                }
                val old = base as? LoginItem
                (old ?: LoginItem(id = newId("l"), space = d.space, updated = now, title = "", username = "", passwordSecretId = "", url = ""))
                    .copy(
                        space = d.space, updated = now, title = d.title.trim(), username = d.username.trim(),
                        passwordSecretId = sealOrNone(d.password), url = d.url.trim(), category = d.category,
                        fields = fields, notes = d.notes.trim(), leaked = (old?.leaked ?: false) && !passwordChanged,
                    )
            }
            is ItemDraft.Card -> {
                val digits = d.number.filter { it.isDigit() }
                ((base as? CardItem) ?: CardItem(newId("c"), d.space, updated = now, title = "", holder = "", numberSecretId = "", last4 = "", expiry = "", codeSecretId = ""))
                    .copy(
                        space = d.space, updated = now, title = d.title.trim(), holder = d.holder.trim(),
                        numberSecretId = sealOrNone(d.number.trim()), last4 = digits.takeLast(4),
                        expiry = d.expiry.trim(), codeSecretId = sealOrNone(d.code.trim()),
                    )
            }
            is ItemDraft.Wifi ->
                ((base as? WifiItem) ?: WifiItem(newId("w"), d.space, updated = now, title = "", network = "", passwordSecretId = "", security = ""))
                    .copy(
                        space = d.space, updated = now, title = d.title.trim(), network = d.network.trim(),
                        passwordSecretId = sealOrNone(d.password), security = d.security.trim(),
                    )
        }
        save(item)
        return item
    }

    suspend fun updateLoginPassword(login: LoginItem, newPlaintext: String): LoginItem {
        // The old ciphertext is left for collectGarbage(): other items may still point at it.
        val newId = sealValue(newPlaintext)
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

    suspend fun save(item: Item) {
        when (item) {
            is NoteItem -> db.noteDao().upsert(item.toEntity())
            is LoginItem -> db.loginDao().upsert(item.toEntity())
            is CardItem -> db.cardDao().upsert(item.toEntity())
            is WifiItem -> db.wifiDao().upsert(item.toEntity())
        }
    }

    /** Removes an item for good (used for empty notes and for Recently Deleted after 30 days). */
    suspend fun hardDelete(item: Item) {
        when (item) {
            is NoteItem -> db.noteDao().deleteById(item.id)
            is LoginItem -> db.loginDao().deleteById(item.id)
            is CardItem -> db.cardDao().deleteById(item.id)
            is WifiItem -> db.wifiDao().deleteById(item.id)
        }
    }

    /** "Kept for 30 days, then removed": drops anything deleted longer ago than that. */
    suspend fun purgeExpired(now: Long = System.currentTimeMillis()) {
        val before = now - RECENTLY_DELETED_MS
        db.noteDao().purgeDeletedBefore(before)
        db.loginDao().purgeDeletedBefore(before)
        db.cardDao().purgeDeletedBefore(before)
        db.wifiDao().purgeDeletedBefore(before)
    }

    /**
     * Deletes ciphertext no item points at any more: chips removed from a note, items purged
     * from Recently Deleted. Run at startup and after deleting for good, once the open note is saved, so a live chip is never lost.
     */
    suspend fun collectGarbage() {
        val used = HashSet<String>()
        db.noteDao().all().forEach { n ->
            NoteBlockJson.decode(n.blocksJson).forEach { b -> b.segments.forEach { if (it is Segment.Seal) used.add(it.secretId) } }
        }
        db.loginDao().all().forEach { l ->
            used.add(l.passwordSecretId)
            l.totpSecretId?.let(used::add)
            FieldsJson.decode(l.fieldsJson).forEach { f -> f.secretId?.let(used::add) }
        }
        db.cardDao().all().forEach { used.add(it.numberSecretId); used.add(it.codeSecretId) }
        db.wifiDao().all().forEach { used.add(it.passwordSecretId) }
        db.secretDao().allIds().filterNot { it in used }.forEach { db.secretDao().delete(it) }
    }

    suspend fun seedSampleData() = SampleData.seed(this)

    companion object {
        const val RECENTLY_DELETED_MS = 30L * 86_400_000L

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
    category = runCatching { Category.valueOf(category) }.getOrDefault(Category.LOGIN),
    fields = FieldsJson.decode(fieldsJson), notes = notes,
)
private fun LoginItem.toEntity() = LoginEntity(
    id = id, space = space.name, pinned = pinned, updated = updated, deletedAt = deletedAt,
    title = title, username = username, passwordSecretId = passwordSecretId, url = url,
    isRoot = isRoot, totpSecretId = totpSecretId, hasPasskey = hasPasskey, leaked = leaked, supports2FA = supports2FA,
    category = category.name, fieldsJson = FieldsJson.encode(fields), notes = notes,
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
