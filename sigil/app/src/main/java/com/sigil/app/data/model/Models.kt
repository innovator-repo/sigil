package com.sigil.app.data.model

/**
 * A Space partitions items the way the prototype's sidebar does. Spaces are local to this
 * device: Sigil has no sync or sharing, so unlike the prototype it never says a Space is shared.
 */
enum class Space(val label: String) {
    PERSONAL("Personal"),
    FAMILY("Family"),
    WORK("Work"),
}

/** One run of a note block's content: either plain text, or a sealed secret rendered as a chip. */
sealed class Segment {
    data class Text(val value: String) : Segment()
    data class Seal(val secretId: String, val label: String) : Segment()
}

enum class BlockType { PARAGRAPH, CHECKLIST, HEADING }

/** One line/paragraph of a note. The first block in a note is rendered as its title. */
data class NoteBlock(
    val id: String,
    val type: BlockType = BlockType.PARAGRAPH,
    val done: Boolean = false,
    val segments: List<Segment> = listOf(Segment.Text("")),
) {
    /** Plain text with seals collapsed to "<label> ••••••", mirroring the web app's blockText(). */
    fun plainText(): String = segments.joinToString("") { s ->
        when (s) {
            is Segment.Text -> s.value
            is Segment.Seal -> "${s.label} ••••••"
        }
    }

    fun textOnly(): String = segments.filterIsInstance<Segment.Text>().joinToString("") { it.value }

    val isBlank: Boolean get() = segments.all { it is Segment.Text && it.value.isBlank() }
}

sealed class Item {
    abstract val id: String
    abstract val space: Space
    abstract val pinned: Boolean
    abstract val updated: Long
    abstract val deletedAt: Long?

    val isDeleted: Boolean get() = deletedAt != null
}

data class NoteItem(
    override val id: String,
    override val space: Space,
    override val pinned: Boolean = false,
    override val updated: Long,
    override val deletedAt: Long? = null,
    val blocks: List<NoteBlock>,
) : Item() {
    /** First block's text, or "New Note" when empty - matches noteParts(item).title. */
    val title: String get() = blocks.firstOrNull()?.plainText()?.trim()?.ifBlank { null } ?: "New Note"

    /** First non-empty block after the title, for the list row's preview text. */
    val preview: String get() =
        blocks.drop(1).map { it.plainText().trim() }.firstOrNull { it.isNotBlank() } ?: "No additional text"

    val searchableText: String get() = blocks.joinToString(" ") { it.plainText() }
}

/**
 * An extra field on a password item, e.g. a bank's IFSC (plain) or MPIN (sealed). A sealed
 * field holds only [secretId]; its value lives encrypted in the vault like any password.
 */
data class CustomField(val id: String, val label: String, val value: String = "", val secretId: String? = null) {
    val isSecret: Boolean get() = secretId != null
}

/** A field a [Category] starts with. */
data class FieldTemplate(val label: String, val secret: Boolean)

/**
 * What kind of thing a password item is. Each starts with the fields that kind usually
 * needs and names its two main fields the way that kind does ("Customer ID", "Client ID").
 */
enum class Category(
    val label: String,
    val usernameLabel: String,
    val passwordLabel: String,
    val fields: List<FieldTemplate> = emptyList(),
    val hint: String,
) {
    LOGIN("Login", "User name", "Password", hint = "Websites and apps: email, Microsoft, Spotify, Jira…"),
    BANK(
        "Bank Account", "Customer ID", "Net banking password",
        listOf(
            FieldTemplate("Account number", true), FieldTemplate("IFSC", false),
            FieldTemplate("Net banking PIN", true), FieldTemplate("MPIN", true), FieldTemplate("App MPIN", true),
        ),
        hint = "Customer ID, passwords, PINs, account number, IFSC",
    ),
    TRADING("Trading & Investing", "Client ID", "Password", listOf(FieldTemplate("PIN", true)), hint = "Brokers and charting: Dhan, Groww, TradingView…"),
    GOVERNMENT(
        "Government Portal", "User ID / UAN / PAN", "Password", listOf(FieldTemplate("Registered mobile", false)),
        hint = "UAN, ITR, MCA, DigiLocker…",
    ),
    EDUCATION(
        "University", "UID", "Password", listOf(FieldTemplate("Student ID", false), FieldTemplate("DEB ID", false)),
        hint = "Student IDs, portal password and link",
    ),
    SIGNATURE(
        "Digital Signature", "User name", "Password",
        listOf(FieldTemplate("Challenge code", true), FieldTemplate("Token password", true)),
        hint = "DSC tokens like eMudhra: challenge code, token password",
    ),
    LICENSE("Software License", "Licensed to", "Account password", listOf(FieldTemplate("License key", true), FieldTemplate("Email", false)), hint = "Serial keys: Internet Download Manager…"),
    LOCK("Lock & PIN", "Device or place", "Code", hint = "Hardware locks, safes, door and gate codes"),
    OTHER("Other", "User name", "Password", hint = "Anything else, with your own fields"),
}

data class LoginItem(
    override val id: String,
    override val space: Space,
    override val pinned: Boolean = false,
    override val updated: Long,
    override val deletedAt: Long? = null,
    val title: String,
    val username: String,
    val passwordSecretId: String,
    val url: String,
    val isRoot: Boolean = false,
    val totpSecretId: String? = null,
    val hasPasskey: Boolean = false,
    val leaked: Boolean = false,
    val supports2FA: Boolean = false,
    val category: Category = Category.LOGIN,
    val fields: List<CustomField> = emptyList(),
    val notes: String = "",
) : Item() {
    /** Some kinds (a license, a lock) may have no main password; it's stored as an empty id. */
    val hasPassword: Boolean get() = passwordSecretId.isNotEmpty()
}

data class CardItem(
    override val id: String,
    override val space: Space,
    override val pinned: Boolean = false,
    override val updated: Long,
    override val deletedAt: Long? = null,
    val title: String,
    val holder: String,
    val numberSecretId: String,
    val last4: String,
    val expiry: String,
    val codeSecretId: String,
) : Item()

data class WifiItem(
    override val id: String,
    override val space: Space,
    override val pinned: Boolean = false,
    override val updated: Long,
    override val deletedAt: Long? = null,
    val title: String,
    val network: String,
    val passwordSecretId: String,
    val security: String,
) : Item()

fun Item.isPassword(): Boolean = this !is NoteItem

fun Item.titleText(): String = when (this) {
    is NoteItem -> title
    is LoginItem -> title
    is CardItem -> title
    is WifiItem -> title
}

/** The subtitle shown under a list row's title (web app's subOf()). */
fun Item.subtitleText(): String = when (this) {
    is NoteItem -> preview
    is LoginItem -> username.ifBlank { if (category == Category.LOGIN) "" else category.label }
    is CardItem -> "•••• $last4"
    is WifiItem -> network
}

fun Item.searchableBody(): String = when (this) {
    is NoteItem -> searchableText
    is LoginItem -> listOf(username, url, category.label, notes)
        .plus(fields.flatMap { listOf(it.label, if (it.isSecret) "" else it.value) })
        .joinToString(" ")
    is CardItem -> holder
    is WifiItem -> "$network $security"
}
