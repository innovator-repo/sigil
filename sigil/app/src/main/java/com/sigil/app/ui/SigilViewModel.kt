package com.sigil.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sigil.app.SigilApplication
import com.sigil.app.data.Repository
import com.sigil.app.data.model.CardItem
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
import kotlinx.coroutines.flow.MutableStateFlow
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
) {
    fun item(id: String?): Item? = id?.let { i -> items.firstOrNull { it.id == i } }
    val selected: Item? get() = item(selectedId)
    fun listItems(section: Section = this.section): List<Item> =
        items.filter(section::matches).sortedByDescending { it.updated }
    fun count(section: Section): Int = items.count(section::matches)
    val flaggedIds: Set<String> get() = checkup.open.mapTo(HashSet()) { it.item.id }
    /** Anything secret on screen right now, which keeps screenshots and the app switcher blank. */
    val showsSecrets: Boolean get() = revealed.isNotEmpty() || openChips.isNotEmpty() ||
        overlay is Overlay.Generator || overlay is Overlay.TotpSetup
}

class SigilViewModel(app: Application) : AndroidViewModel(app) {
    private val sigil = app as SigilApplication
    private val repo: Repository = sigil.repository
    private val prefs = sigil.prefs

    private val _state = MutableStateFlow(UiState(locked = prefs.getBoolean(SigilApplication.KEY_LOCKED, false)))
    val state: StateFlow<UiState> = _state.asStateFlow()

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
        leaveNote()
        hideAll()
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

    fun setSidebarOpen(open: Boolean) = _state.update { it.copy(sidebarOpen = open) }

    // ───────── Notes ─────────

    fun newNote(seed: String = "") {
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

    fun noteSealed() = toast("Sealed. It stays hidden until you reveal it.", SigilIcon.Lock)

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
        toast("Moved to Recently Deleted.", SigilIcon.Trash, "Undo") {
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
        toast("Recovered.", SigilIcon.Check)
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
            revealJobs.remove(key)?.cancel()
            revealJobs[key] = launch {
                delay(REVEAL_MS)
                _state.update { it.copy(revealed = it.revealed - key) }
            }
        }
    }

    fun hide(key: String) {
        revealJobs.remove(key)?.cancel()
        _state.update { it.copy(revealed = it.revealed - key) }
    }

    fun openChip(secretId: String) {
        viewModelScope.launch {
            val value = repo.revealValue(secretId)
            val key = "chip:$secretId"
            _state.update { it.copy(openChips = it.openChips + (secretId to Revealed(value, System.currentTimeMillis() + REVEAL_MS))) }
            revealJobs.remove(key)?.cancel()
            revealJobs[key] = launch {
                delay(REVEAL_MS)
                _state.update { it.copy(openChips = it.openChips - secretId) }
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
        toast(if (sensitive) "$label copied. Clears in 60 seconds." else "$label copied.", SigilIcon.Copy)
    }

    suspend fun secretValue(secretId: String): String = repo.revealValue(secretId)

    suspend fun secretLength(secretId: String): Int = repo.secretLength(secretId)

    // ───────── Lock ─────────

    fun lock() {
        flush()
        hideAll()
        presenceUntil = 0
        prefs.edit().putBoolean(SigilApplication.KEY_LOCKED, true).apply()
        _state.update { it.copy(locked = true, overlay = null, sidebarOpen = false) }
    }

    fun unlocked() {
        prefs.edit().putBoolean(SigilApplication.KEY_LOCKED, false).apply()
        _state.update { it.copy(locked = false) }
        toast("Unlocked.", SigilIcon.Lock)
    }

    /** Leaving the app hides every revealed value, so it's never in the app switcher or there on return. */
    fun onBackgrounded() {
        flush()
        hideAll()
    }

    // ───────── Overlays ─────────

    fun openFind(initial: String = "") = _state.update { it.copy(overlay = Overlay.Find(initial), sidebarOpen = false) }
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
            toast("${login.title} now has its own password.", SigilIcon.Check)
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
            toast("Verification codes are on for ${login.title}.", SigilIcon.Check)
        }
    }

    // ───────── Check-up ─────────

    fun fix(issue: SecurityIssue) {
        if (issue.type == IssueType.NO_2FA) openTotpSetup(issue.item.id) else openGenerator(issue.item.id)
    }

    fun snooze(issue: SecurityIssue) {
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
        const val PRESENCE_MS = 60_000L
        private const val SAVE_DELAY_MS = 300L
    }
}
