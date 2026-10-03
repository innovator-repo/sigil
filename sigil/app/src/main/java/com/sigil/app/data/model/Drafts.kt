package com.sigil.app.data.model

/** One field in the editor, with its value in the clear while the editor is open. */
data class DraftField(val id: String, val label: String, val value: String, val secret: Boolean)

/** What the item editor works on: every kind of password item, values decrypted for editing. */
sealed interface ItemDraft {
    val space: Space
    val title: String

    data class Login(
        override val space: Space,
        override val title: String,
        val category: Category,
        val username: String,
        val password: String,
        val url: String,
        val fields: List<DraftField>,
        val notes: String,
    ) : ItemDraft

    data class Card(
        override val space: Space,
        override val title: String,
        val holder: String,
        val number: String,
        val expiry: String,
        val code: String,
    ) : ItemDraft

    data class Wifi(
        override val space: Space,
        override val title: String,
        val network: String,
        val password: String,
        val security: String,
    ) : ItemDraft
}
