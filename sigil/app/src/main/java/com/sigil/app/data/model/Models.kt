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
) : Item()

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
    is LoginItem -> username
    is CardItem -> "•••• $last4"
    is WifiItem -> network
}

fun Item.searchableBody(): String = when (this) {
    is NoteItem -> searchableText
    is LoginItem -> "$username $url"
    is CardItem -> holder
    is WifiItem -> "$network $security"
}
