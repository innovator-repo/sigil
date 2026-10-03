package com.sigil.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sigil.app.SigilApplication
import com.sigil.app.data.Repository
import com.sigil.app.data.model.CardItem
import com.sigil.app.data.model.DraftField
import com.sigil.app.data.model.ItemDraft
import com.sigil.app.data.model.Item
import com.sigil.app.data.model.LoginItem
import com.sigil.app.data.model.NoteItem
import com.sigil.app.data.model.Space
import com.sigil.app.data.model.WifiItem
import com.sigil.app.data.model.isPassword
import com.sigil.app.editor.NoteDoc
import com.sigil.app.logic.CheckupResult
import com.sigil.app.logic.IssueType
import com.sigil.app.logic.SecurityCheckup
import com.sigil.app.logic.SecurityIssue
import com.sigil.app.ui.theme.SigilIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Which pane is in front on a phone, where only one fits (the prototype's `state.view`). */
enum class Pane { SIDEBAR, LIST, DETAIL }

enum class Section(val label: String, val icon: SigilIcon, val space: Space? = null) {
    ALL("Everything", SigilIcon.All),
    NOTES("Notes", SigilIcon.Note),
    PASSWORDS("Passwords", SigilIcon.Key),
    PINNED("Pinned", SigilIcon.Pin),
    PERSONAL("Personal", SigilIcon.Person, Space.PERSONAL),
    FAMILY("Family", SigilIcon.People, Space.FAMILY),
    WORK("Work", SigilIcon.Briefcase, Space.WORK),
    CHECKUP("Check-up", SigilIcon.Shield),
    DELETED("Recently Deleted", SigilIcon.Trash),
    ;

    fun matches(i: Item): Boolean = when (this) {
        ALL -> !i.isDeleted
        NOTES -> !i.isDeleted && i is NoteItem
        PASSWORDS -> !i.isDeleted && i.isPassword()
        PINNED -> !i.isDeleted && i.pinned
        PERSONAL, FAMILY, WORK -> !i.isDeleted && i.space == space
        DELETED -> i.isDeleted
        CHECKUP -> false
    }
}

/** A secret currently shown in the clear, and when it hides itself again. */
data class Revealed(val value: String, val until: Long)

sealed interface Overlay {
    data class Find(val initial: String = "") : Overlay
    /** The password generator: a new login when [changeItemId] is null, else a new password for that login. */
    data class Generator(val changeItemId: String? = null) : Overlay
    data class TotpSetup(val itemId: String) : Overlay
    /** "What are you saving?": the kinds of password item to start from. */
    data object NewItem : Overlay
    /**
     * The item editor: an existing item ([itemId]), or a new one of [category], or a new card or
     * Wi‑Fi network. Its secret values are decrypted for as long as it's open.
     */
    data class Editor(
        val itemId: String? = null,
        val category: com.sigil.app.data.model.Category? = null,
        val card: Boolean = false,
        val wifi: Boolean = false,
    ) : Overlay
    /** "Delete forever?" for items already in Recently Deleted. */
    data class ConfirmDelete(val ids: Set<String>) : Overlay
}

data class ToastMessage(
    val id: Long,
    val text: String,
    val icon: SigilIcon,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
)

data class FixedEntry(val item: Item, val what: String)

data class UiState(
    val ready: Boolean = false,
    val items: List<Item> = emptyList(),
    val section: Section = Section.ALL,
    val selectedId: String? = null,
    /** Sigil opens on Everything (or resumes where you were, see [SigilViewModel.restorePlace]). */
    val pane: Pane = Pane.LIST,
    val sidebarOpen: Boolean = false,
    val checkup: CheckupResult = CheckupResult(emptyList(), 0, 0),
    val fixed: List<FixedEntry> = emptyList(),
    /** Revealed fields, keyed "itemId:field". */
    val revealed: Map<String, Revealed> = emptyMap(),
    /** Opened chips in notes, keyed by secret id. */
    val openChips: Map<String, Revealed> = emptyMap(),
    val locked: Boolean = false,
    val overlay: Overlay? = null,
    val toast: ToastMessage? = null,
    /** A note that was just created and should take the keyboard. */
    val focusNoteId: String? = null,
    /** Ids picked in multi-select; null when not selecting. */
    val selection: Set<String>? = null,
) {
    val selecting: Boolean get() = selection != null

    fun item(id: String?): Item? = id?.let { i -> items.firstOrNull { it.id == i } }
    val selected: Item? get() = item(selectedId)
    fun listItems(section: Section = this.section): List<Item> =
        items.filter(section::matches).sortedByDescending { it.updated }
    fun count(section: Section): Int = items.count(section::matches)
    val flaggedIds: Set<String> get() = checkup.open.mapTo(HashSet()) { it.item.id }
    /** Anything secret on screen right now, which keeps screenshots and the app switcher blank. */
    val showsSecrets: Boolean get() = revealed.isNotEmpty() || openChips.isNotEmpty() ||
        overlay is Overlay.Generator || overlay is Overlay.TotpSetup || overlay is Overlay.Editor
}

class SigilViewModel(app: Application) : AndroidViewModel(app) {
    private val sigil = app as SigilApplication
    private val repo: Repository = sigil.repository
    private val prefs = sigil.prefs

    private val _state = MutableStateFlow(UiState(locked = prefs.getBoolean(SigilApplication.KEY_LOCKED, false)))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _haptics = MutableSharedFlow<Haptic>(extraBufferCapacity = 8)

    /** Touch feedback for what just happened; the activity plays it. */
    val haptics: SharedFlow<Haptic> = _haptics.asSharedFlow()

    fun buzz(h: Haptic) {
        _haptics.tryEmit(h)
    }

    private var repoItems: List<Item> = emptyList()

    /**
     * Optimistic versions of items changed in this session but maybe not yet saved - the open
     * note while typing, a just-pinned or just-deleted item - so the UI never waits on disk.
     * Each is dropped once the database reports the same item back.
     */
    private val overrides = LinkedHashMap<String, Item>()
    private val removed = HashSet<String>()

    private val writes = Mutex()
    private var saveJob: Job? = null
    private var checkupJob: Job? = null
    private var toastJob: Job? = null
    private var toastSeq = 0L
    private val revealJobs = HashMap<String, Job>()
    private var presenceUntil = 0L
    private var snoozed: List<String> = emptyList()
    private var lastLogins: List<LoginItem>? = null
    private val dismissedSecrets = HashSet<String>()
    private var noLockHintShown = false

    init {
        viewModelScope.launch {
            sigil.startup.join()
            repo.observeItems().collect { list ->
                repoItems = list
                publishItems()
                if (!_state.value.ready) {
                    _state.update { s -> s.copy(ready = true, selectedId = s.selectedId ?: s.listItems().firstOrNull()?.id) }
                    restorePlace()
                }
                val logins = list.filterIsInstance<LoginItem>()
                if (logins != lastLogins) {
                    lastLogins = logins
                    recomputeCheckup()
                }
            }
        }
    }

    private fun publishItems() {
        overrides.entries.removeAll { (id, o) -> id != _state.value.selectedId && repoItems.any { it == o } }
        removed.retainAll { id -> repoItems.any { it.id == id } }
        val merged = repoItems.filter { it.id !in removed }.map { overrides[it.id] ?: it } +
            overrides.values.filter { o -> o.id !in removed && repoItems.none { it.id == o.id } }
        _state.update { it.copy(items = merged) }
    }

    private fun current(id: String?): Item? = _state.value.item(id)

    private fun override(item: Item) {
        overrides[item.id] = item
        publishItems()
    }

    /** Writes run in order, and on the app scope so a save in flight survives the screen closing. */
    private fun write(block: suspend () -> Unit) = sigil.appScope.launch { writes.withLock { block() } }

    // ───────── Navigation ─────────

    fun go(section: Section) {
        buzz(Haptic.TICK)
        leaveNote()
        hideAll()
        _state.update { s -> s.copy(selection = null) }
        _state.update { s ->
            if (section == Section.CHECKUP) {
                s.copy(section = section, pane = Pane.DETAIL, sidebarOpen = false)
            } else {
                val keep = s.selected?.takeIf(section::matches)?.id
                s.copy(section = section, pane = Pane.LIST, sidebarOpen = false, selectedId = keep ?: s.listItems(section).firstOrNull()?.id)
            }
        }
    }

    fun select(id: String) {
        buzz(Haptic.TICK)
        if (id != _state.value.selectedId) leaveNote()
        hideAll()
        _state.update { it.copy(selectedId = id, pane = Pane.DETAIL, overlay = null) }
    }

    /** Opens an item from search, switching to Everything if the current section doesn't show it. */
    fun open(id: String) {
        val item = current(id) ?: return
        if (!_state.value.section.matches(item)) {
            leaveNote()
            _state.update { it.copy(section = Section.ALL) }
        }
        select(id)
    }

    fun showSidebar() = _state.update { it.copy(pane = Pane.SIDEBAR) }

    fun showList() {
        leaveNote()
        hideAll()
        _state.update { it.copy(pane = Pane.LIST) }
    }

    fun setSidebarOpen(open: Boolean) {
        if (open) buzz(Haptic.TICK)
        _state.update { it.copy(sidebarOpen = open) }
    }

    // ───────── Notes ─────────

    fun newNote(seed: String = "") {
        buzz(Haptic.CLICK)
        leaveNote()
        val s = _state.value
        val doc = NoteDoc.withTitle(seed.trim())
        val note = NoteItem(
            id = Repository.newId("n"),
            space = s.section.space ?: Space.PERSONAL,
            updated = System.currentTimeMillis(),
            blocks = doc.toBlocks(),
        )
        override(note)
        val section = if (s.section == Section.ALL || s.section == Section.NOTES || s.section.space != null) s.section else Section.NOTES
        _state.update { it.copy(section = section, selectedId = note.id, pane = Pane.DETAIL, overlay = null, focusNoteId = note.id) }
        if (seed.isNotBlank()) {
            write { repo.saveNote(note) }
            toast("Kept. It saves as you type.", SigilIcon.Check)
        }
    }

    fun consumeFocusRequest() = _state.update { it.copy(focusNoteId = null) }

    /** Called on every edit; the note is saved a moment after typing pauses, and on leaving it. */
    fun onNoteEdited(id: String, doc: NoteDoc) {
        val base = current(id) as? NoteItem ?: return
        if (NoteDoc.fromBlocks(base.blocks) == doc) return
        val updated = base.copy(blocks = doc.toBlocks(), updated = System.currentTimeMillis())
        override(updated)
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            writes.withLock { repo.saveNote(updated) }
        }
    }

    /** Saves the open note now. Empty notes are thrown away instead, like the prototype's discardIfEmpty(). */
    private fun leaveNote() {
        val note = current(_state.value.selectedId) as? NoteItem ?: return
        saveJob?.cancel()
        if (!note.isDeleted && NoteDoc.fromBlocks(note.blocks).isBlank) {
            overrides.remove(note.id)
            removed.add(note.id)
            publishItems()
            write { repo.hardDelete(note) }
        } else if (overrides[note.id] == note) {
            write { repo.saveNote(note) }
        }
    }

    fun flush() {
        val note = current(_state.value.selectedId) as? NoteItem ?: return
        saveJob?.cancel()
        if (overrides[note.id] == note) write { repo.saveNote(note) }
    }

    suspend fun sealSecret(value: String): String = writes.withLock { repo.sealValue(value) }

    fun noteSealed() = buzz(Haptic.SEAL).also { toast("Sealed. It stays hidden until you reveal it.", SigilIcon.Lock) }

    fun dismissSecret(value: String) {
        dismissedSecrets.add(value)
    }

    fun isDismissed(value: String): Boolean = value in dismissedSecrets

    // ───────── Item actions ─────────

    fun togglePin() {
        val item = _state.value.selected ?: return
        val pinned = !item.pinned
        val updated = when (item) {
            is NoteItem -> item.copy(pinned = pinned)
            is LoginItem -> item.copy(pinned = pinned)
            is CardItem -> item.copy(pinned = pinned)
            is WifiItem -> item.copy(pinned = pinned)
        }
        saveJob?.cancel()
        override(updated)
        write { repo.save(updated) }
        buzz(if (pinned) Haptic.CLICK else Haptic.TICK)
        toast(if (pinned) "Pinned." else "Unpinned.", SigilIcon.Pin)
    }

    fun delete() {
        val item = _state.value.selected ?: return
        saveJob?.cancel()
        hideAll()
        val now = System.currentTimeMillis()
        val deleted = withDeletedAt(item, now)
        override(deleted)
        write { repo.save(deleted) }
        _state.update { s -> s.copy(selectedId = s.listItems().firstOrNull()?.id, pane = Pane.LIST) }
        buzz(Haptic.THUD)
        toast("Moved to Recently Deleted.", SigilIcon.Trash, "Undo") {
            buzz(Haptic.TICK)
            val restored = withDeletedAt(deleted, null)
            override(restored)
            write { repo.save(restored) }
            _state.update { it.copy(selectedId = item.id) }
        }
    }

    fun recover() {
        val item = _state.value.selected ?: return
        val restored = withDeletedAt(item, null)
        override(restored)
        write { repo.save(restored) }
        _state.update { it.copy(section = Section.ALL, selectedId = item.id) }
        buzz(Haptic.SUCCESS)
        toast("Recovered.", SigilIcon.Check)
    }

    // ───────── Multi-select ─────────

    fun startSelection(id: String) {
        buzz(Haptic.CLICK)
        _state.update { it.copy(selection = setOf(id)) }
    }

    fun toggleSelection(id: String) {
        buzz(Haptic.TICK)
        _state.update { s ->
            val sel = s.selection ?: emptySet()
            s.copy(selection = if (id in sel) sel - id else sel + id)
        }
    }

    fun selectAll() {
        buzz(Haptic.TICK)
        _state.update { s ->
            val all = s.listItems().mapTo(HashSet()) { it.id }
            s.copy(selection = if (s.selection == all) emptySet() else all)
        }
    }

    /** "Select" in the toolbar: selection mode with nothing picked yet. */
    fun beginSelection() {
        buzz(Haptic.TICK)
        _state.update { it.copy(selection = emptySet()) }
    }

    fun endSelection() = _state.update { it.copy(selection = null) }

    private fun selectedItems(): List<Item> {
        val s = _state.value
        val sel = s.selection ?: return emptyList()
        return s.listItems().filter { it.id in sel }
    }

    /** Pins everything picked, or unpins it all if it's all pinned already. */
    fun pinSelected() {
        val items = selectedItems().ifEmpty { return }
        val pin = items.any { !it.pinned }
        saveJob?.cancel()
        val updated = items.map { withPinned(it, pin) }
        updated.forEach { override(it) }
        write { updated.forEach { repo.save(it) } }
        buzz(if (pin) Haptic.CLICK else Haptic.TICK)
        endSelection()
        toast(if (pin) "Pinned ${count(items.size)}." else "Unpinned ${count(items.size)}.", SigilIcon.Pin)
    }

    fun moveSelected(space: Space) {
        val items = selectedItems().ifEmpty { return }
        saveJob?.cancel()
        val now = System.currentTimeMillis()
        val updated = items.map { withSpace(it, space, now) }
        updated.forEach { override(it) }
        write { updated.forEach { repo.save(it) } }
        buzz(Haptic.SUCCESS)
        endSelection()
        toast("Moved ${count(items.size)} to ${space.label}.", SigilIcon.Check)
    }

    fun deleteSelected() {
        val items = selectedItems().filter { !it.isDeleted }.ifEmpty { return }
        saveJob?.cancel()
        hideAll()
        val now = System.currentTimeMillis()
        val deleted = items.map { withDeletedAt(it, now) }
        deleted.forEach { override(it) }
        write { deleted.forEach { repo.save(it) } }
        val gone = items.mapTo(HashSet()) { it.id }
        _state.update { s ->
            s.copy(selection = null, selectedId = if (s.selectedId in gone) s.listItems().firstOrNull()?.id else s.selectedId)
        }
        buzz(Haptic.THUD)
        toast("Moved ${count(items.size)} to Recently Deleted.", SigilIcon.Trash, "Undo") {
            buzz(Haptic.TICK)
            val restored = deleted.map { withDeletedAt(it, null) }
            restored.forEach { override(it) }
            write { restored.forEach { repo.save(it) } }
        }
    }

    fun recoverSelected() {
        val items = selectedItems().filter { it.isDeleted }.ifEmpty { return }
        val restored = items.map { withDeletedAt(it, null) }
        restored.forEach { override(it) }
        write { restored.forEach { repo.save(it) } }
        buzz(Haptic.SUCCESS)
        endSelection()
        toast("Recovered ${count(items.size)}.", SigilIcon.Check)
    }

    /** Asks before erasing items from Recently Deleted for good. */
    fun confirmDeleteForever(ids: Set<String>) {
        if (ids.isEmpty()) return
        buzz(Haptic.NUDGE)
        _state.update { it.copy(overlay = Overlay.ConfirmDelete(ids)) }
    }

    fun confirmDeleteSelected() = confirmDeleteForever(selectedItems().filter { it.isDeleted }.mapTo(HashSet()) { it.id })

    fun deleteForever(ids: Set<String>) {
        // Save the open note first, so the garbage pass below sees every chip it still uses.
        flush()
        val items = _state.value.items.filter { it.id in ids && it.isDeleted }
        items.forEach {
            overrides.remove(it.id)
            removed.add(it.id)
        }
        publishItems()
        write {
            items.forEach { repo.hardDelete(it) }
            repo.collectGarbage()
        }
        _state.update { s ->
            s.copy(overlay = null, selection = null, selectedId = if (s.selectedId in ids) s.listItems().firstOrNull()?.id else s.selectedId, pane = if (s.selectedId in ids) Pane.LIST else s.pane)
        }
        buzz(Haptic.THUD)
        toast(if (items.size == 1) "Deleted for good." else "Deleted ${items.size} items for good.", SigilIcon.Trash)
    }

    private fun count(n: Int) = if (n == 1) "1 item" else "$n items"

    private fun withPinned(item: Item, pinned: Boolean): Item = when (item) {
        is NoteItem -> item.copy(pinned = pinned)
        is LoginItem -> item.copy(pinned = pinned)
        is CardItem -> item.copy(pinned = pinned)
        is WifiItem -> item.copy(pinned = pinned)
    }

    private fun withSpace(item: Item, space: Space, now: Long): Item = when (item) {
        is NoteItem -> item.copy(space = space, updated = now)
        is LoginItem -> item.copy(space = space, updated = now)
        is CardItem -> item.copy(space = space, updated = now)
        is WifiItem -> item.copy(space = space, updated = now)
    }

    private fun withDeletedAt(item: Item, at: Long?): Item = when (item) {
        is NoteItem -> item.copy(deletedAt = at)
        is LoginItem -> item.copy(deletedAt = at)
        is CardItem -> item.copy(deletedAt = at)
        is WifiItem -> item.copy(deletedAt = at)
    }

    // ───────── Presence, reveal, copy ─────────

    fun isPresent(): Boolean = System.currentTimeMillis() < presenceUntil

    /** Face ID's "presence stays verified for a minute", with the system biometric prompt. */
    fun markPresent() {
        presenceUntil = System.currentTimeMillis() + PRESENCE_MS
    }

    fun noScreenLock() {
        markPresent()
        if (!noLockHintShown) {
            noLockHintShown = true
            toast("Set up a screen lock so sealed things need you present.", SigilIcon.Lock)
        }
    }

    fun reveal(key: String, secretId: String) {
        viewModelScope.launch {
            val value = repo.revealValue(secretId)
            val until = System.currentTimeMillis() + REVEAL_MS
            _state.update { it.copy(revealed = it.revealed + (key to Revealed(value, until))) }
            buzz(Haptic.REVEAL)
            revealJobs.remove(key)?.cancel()
            revealJobs[key] = launch {
                delay(REVEAL_MS)
                _state.update { it.copy(revealed = it.revealed - key) }
                buzz(Haptic.HIDE)
            }
        }
    }

    fun hide(key: String) {
        if (key in _state.value.revealed) buzz(Haptic.HIDE)
        revealJobs.remove(key)?.cancel()
        _state.update { it.copy(revealed = it.revealed - key) }
    }

    fun openChip(secretId: String) {
        viewModelScope.launch {
            val value = repo.revealValue(secretId)
            val key = "chip:$secretId"
            _state.update { it.copy(openChips = it.openChips + (secretId to Revealed(value, System.currentTimeMillis() + REVEAL_MS))) }
            buzz(Haptic.REVEAL)
            revealJobs.remove(key)?.cancel()
            revealJobs[key] = launch {
                delay(REVEAL_MS)
                _state.update { it.copy(openChips = it.openChips - secretId) }
                buzz(Haptic.HIDE)
            }
        }
    }

    fun hideAll() {
        revealJobs.values.forEach { it.cancel() }
        revealJobs.clear()
        _state.update { it.copy(revealed = emptyMap(), openChips = emptyMap()) }
    }

    fun copySecret(secretId: String, label: String) {
        viewModelScope.launch { copyValue(repo.revealValue(secretId), label) }
    }

    fun copyValue(value: String, label: String, sensitive: Boolean = true) {
        sigil.clipboard.copy(value, sensitive)
        buzz(Haptic.CLICK)
        toast(if (sensitive) "$label copied. Clears in 60 seconds." else "$label copied.", SigilIcon.Copy)
    }

    suspend fun secretValue(secretId: String): String = repo.revealValue(secretId)

    suspend fun secretLength(secretId: String): Int = repo.secretLength(secretId)

    // ───────── Lock ─────────

    fun lock() {
        flush()
        hideAll()
        presenceUntil = 0
        buzz(Haptic.THUD)
        prefs.edit().putBoolean(SigilApplication.KEY_LOCKED, true).apply()
        _state.update { it.copy(locked = true, overlay = null, sidebarOpen = false, selection = null) }
    }

    fun unlocked() {
        prefs.edit().putBoolean(SigilApplication.KEY_LOCKED, false).apply()
        _state.update { it.copy(locked = false) }
        buzz(Haptic.SUCCESS)
        toast("Unlocked.", SigilIcon.Lock)
    }

    /** Leaving the app hides every revealed value, so it's never in the app switcher or there on return. */
    fun onBackgrounded() {
        flush()
        hideAll()
        savePlace()
    }

    /**
     * Where you were when you left. Coming back within [RESUME_MS] (from another app, or after
     * Android reclaimed the process) puts you back on the same item; any later, Sigil opens fresh
     * on Everything, the newest things first.
     */
    private fun savePlace() {
        val s = _state.value
        prefs.edit()
            .putString(KEY_PLACE_SECTION, s.section.name)
            .putString(KEY_PLACE_ITEM, s.selectedId)
            .putString(KEY_PLACE_PANE, s.pane.name)
            .putLong(KEY_PLACE_AT, System.currentTimeMillis())
            .apply()
    }

    private fun restorePlace() {
        val at = prefs.getLong(KEY_PLACE_AT, 0L)
        if (System.currentTimeMillis() - at > RESUME_MS) return
        val section = runCatching { Section.valueOf(prefs.getString(KEY_PLACE_SECTION, null)!!) }.getOrNull() ?: return
        val pane = runCatching { Pane.valueOf(prefs.getString(KEY_PLACE_PANE, null)!!) }.getOrNull() ?: return
        val item = current(prefs.getString(KEY_PLACE_ITEM, null))
        _state.update { s ->
            when {
                section == Section.CHECKUP -> s.copy(section = section, pane = pane)
                item != null && section.matches(item) -> s.copy(section = section, selectedId = item.id, pane = pane)
                else -> s.copy(section = section, pane = if (pane == Pane.DETAIL) Pane.LIST else pane, selectedId = s.listItems(section).firstOrNull()?.id)
            }
        }
    }

    // ───────── Overlays ─────────

    fun openFind(initial: String = "") = buzz(Haptic.TICK).also { _state.update { it.copy(overlay = Overlay.Find(initial), sidebarOpen = false) } }
    fun openGenerator(changeItemId: String? = null) = _state.update { it.copy(overlay = Overlay.Generator(changeItemId), sidebarOpen = false) }
    fun closeOverlay() = _state.update { it.copy(overlay = null) }

    fun createLogin(site: String, user: String, password: String) {
        val url = site.trim().replace(Regex("^https?://", RegexOption.IGNORE_CASE), "").replace(Regex("/.*$"), "")
        val name = url.removePrefix("www.").substringBefore('.')
        val title = name.replaceFirstChar { it.uppercaseChar() }.ifBlank { "Untitled" }
        val s = _state.value
        val space = s.section.space ?: Space.PERSONAL
        closeOverlay()
        viewModelScope.launch {
            val login = writes.withLock { repo.createLogin(space, title, user.trim(), url, password) }
            override(login)
            val section = if (s.section == Section.PASSWORDS || s.section == Section.ALL || s.section.space == space) s.section else Section.PASSWORDS
            leaveNote()
            _state.update { it.copy(section = section, selectedId = login.id, pane = Pane.DETAIL) }
            buzz(Haptic.SUCCESS)
            toast(if (url.isNotEmpty()) "Saved for $url." else "Saved.", SigilIcon.Key)
        }
    }

    fun changePassword(itemId: String, password: String) {
        val login = current(itemId) as? LoginItem ?: return
        val wasIssue = login.leaked || _state.value.checkup.open.any { it.item.id == itemId && it.type == IssueType.REUSED }
        closeOverlay()
        hide("$itemId:pw")
        viewModelScope.launch {
            val updated = writes.withLock { repo.updateLoginPassword(login, password) }
            override(updated)
            if (wasIssue) _state.update { it.copy(fixed = it.fixed + FixedEntry(updated, "New password")) }
            buzz(Haptic.SUCCESS)
            toast("${login.title} now has its own password.", SigilIcon.Check)
        }
    }

    fun openNewItem() = buzz(Haptic.TICK).also { _state.update { it.copy(overlay = Overlay.NewItem, sidebarOpen = false) } }

    fun openEditor(editor: Overlay.Editor) = _state.update { it.copy(overlay = editor, sidebarOpen = false) }

    /** The editor's starting point: an existing item with its sealed values opened, or a template. */
    suspend fun loadDraft(editor: Overlay.Editor): ItemDraft {
        val space = _state.value.section.space ?: Space.PERSONAL
        val item = current(editor.itemId)
        return when {
            item is LoginItem -> ItemDraft.Login(
                item.space, item.title, item.category, item.username, repo.revealValue(item.passwordSecretId), item.url,
                item.fields.map { f -> DraftField(f.id, f.label, f.secretId?.let { repo.revealValue(it) } ?: f.value, f.isSecret) },
                item.notes,
            )
            item is CardItem -> ItemDraft.Card(item.space, item.title, item.holder, repo.revealValue(item.numberSecretId), item.expiry, repo.revealValue(item.codeSecretId))
            item is WifiItem -> ItemDraft.Wifi(item.space, item.title, item.network, repo.revealValue(item.passwordSecretId), item.security)
            editor.card -> ItemDraft.Card(space, "", "", "", "", "")
            editor.wifi -> ItemDraft.Wifi(space, "", "", "", "WPA2/WPA3 Personal")
            else -> {
                val c = editor.category ?: com.sigil.app.data.model.Category.LOGIN
                ItemDraft.Login(space, "", c, "", "", "", c.fields.map { DraftField(Repository.newId("f"), it.label, "", it.secret) }, "")
            }
        }
    }

    fun saveDraft(editor: Overlay.Editor, draft: ItemDraft, original: ItemDraft?) {
        val base = current(editor.itemId)
        val passwordChanged = (draft as? ItemDraft.Login)?.password != (original as? ItemDraft.Login)?.password
        closeOverlay()
        viewModelScope.launch {
            val saved = writes.withLock { repo.saveDraft(base, draft, passwordChanged) }
            override(saved)
            buzz(Haptic.SUCCESS)
            if (base == null) {
                leaveNote()
                val s = _state.value
                val section = if (s.section.matches(saved)) s.section else Section.PASSWORDS
                _state.update { it.copy(section = section, selectedId = saved.id, pane = Pane.DETAIL) }
                toast("Saved.", SigilIcon.Key)
            } else {
                hideAll()
                toast("Changes saved.", SigilIcon.Check)
            }
        }
    }

    fun openTotpSetup(itemId: String) = _state.update { it.copy(overlay = Overlay.TotpSetup(itemId)) }

    fun enableCodes(itemId: String, seed: String) {
        val login = current(itemId) as? LoginItem ?: return
        val wasIssue = _state.value.checkup.open.any { it.item.id == itemId && it.type == IssueType.NO_2FA }
        closeOverlay()
        viewModelScope.launch {
            val updated = writes.withLock { repo.turnOn2FA(login, seed) }
            override(updated)
            if (wasIssue) _state.update { it.copy(fixed = it.fixed + FixedEntry(updated, "Codes on")) }
            buzz(Haptic.SUCCESS)
            toast("Verification codes are on for ${login.title}.", SigilIcon.Check)
        }
    }

    // ───────── Check-up ─────────

    fun fix(issue: SecurityIssue) {
        if (issue.type == IssueType.NO_2FA) openTotpSetup(issue.item.id) else openGenerator(issue.item.id)
    }

    fun snooze(issue: SecurityIssue) {
        buzz(Haptic.TICK)
        snoozed = snoozed.filter { it != issue.key } + issue.key
        recomputeCheckup()
    }

    private fun recomputeCheckup() {
        checkupJob?.cancel()
        checkupJob = viewModelScope.launch {
            val logins = repoItems.filterIsInstance<LoginItem>()
            val result = withContext(Dispatchers.Default) { SecurityCheckup.compute(logins, repo, snoozed) }
            _state.update { it.copy(checkup = result) }
        }
    }

    // ───────── Toast ─────────

    fun toast(text: String, icon: SigilIcon, actionLabel: String? = null, action: (() -> Unit)? = null) {
        val t = ToastMessage(++toastSeq, text, icon, actionLabel, action)
        _state.update { it.copy(toast = t) }
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(if (action != null) 6000 else 3600)
            _state.update { if (it.toast?.id == t.id) it.copy(toast = null) else it }
        }
    }

    /** "That didn't work" - a toast that explains, with the matching tap. */
    fun reject(text: String, icon: SigilIcon) {
        buzz(Haptic.REJECT)
        toast(text, icon)
    }

    fun runToastAction() {
        val t = _state.value.toast ?: return
        _state.update { it.copy(toast = null) }
        t.action?.invoke()
    }

    override fun onCleared() {
        flush()
        super.onCleared()
    }

    companion object {
        const val REVEAL_MS = 30_000L
        const val RESUME_MS = 5 * 60_000L
        private const val KEY_PLACE_SECTION = "place_section"
        private const val KEY_PLACE_ITEM = "place_item"
        private const val KEY_PLACE_PANE = "place_pane"
        private const val KEY_PLACE_AT = "place_at"
        const val PRESENCE_MS = 60_000L
        private const val SAVE_DELAY_MS = 300L
    }
}
