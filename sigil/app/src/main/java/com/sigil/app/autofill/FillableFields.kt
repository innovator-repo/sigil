package com.sigil.app.autofill

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId

/**
 * The two fields Sigil can fill on a sign-in screen, and where the request came from.
 *
 * A web page or app hands autofill a tree of views with no agreed way of saying "this is the
 * password box", so [parse] reads every hint the platform offers, in the order they can be
 * trusted: the autofill hints a careful page sets, then the HTML input type, then the Android
 * input type, then the field's own name. Everything is best effort - when nothing looks like a
 * password Sigil simply offers nothing rather than filling the wrong box.
 */
data class FillableFields(
    val usernameId: AutofillId? = null,
    val passwordId: AutofillId? = null,
    /** The site asking, as the browser reports it: `accounts.google.com`. Empty for an app. */
    val webDomain: String = "",
    /** What is already typed in those fields, which is what a save request is made of. */
    val usernameValue: String = "",
    val passwordValue: String = "",
) {
    val canFill: Boolean get() = passwordId != null || usernameId != null
    val canSave: Boolean get() = passwordValue.isNotEmpty()

    val autofillIds: Array<AutofillId> get() = listOfNotNull(usernameId, passwordId).toTypedArray()

    companion object {
        fun parse(structure: AssistStructure): FillableFields {
            val candidates = ArrayList<Candidate>()
            var webDomain = ""
            for (i in 0 until structure.windowNodeCount) {
                val root = structure.getWindowNodeAt(i).rootViewNode ?: continue
                visit(root) { node ->
                    if (webDomain.isEmpty()) node.webDomain?.takeIf { it.isNotBlank() }?.let { webDomain = it }
                    val id = node.autofillId
                    if (id != null && node.autofillType == View.AUTOFILL_TYPE_TEXT) {
                        candidates.add(Candidate(id, kindOf(node), node.autofillValue?.let { if (it.isText) it.textValue.toString() else null } ?: ""))
                    }
                }
            }

            val passwordAt = candidates.indexOfFirst { it.kind == Kind.PASSWORD }
            val password = candidates.getOrNull(passwordAt)
            // The user name is the one named as such, preferring the last before the password
            // box, since a page with two forms puts its sign-in fields next to each other.
            val username = candidates.take(if (passwordAt >= 0) passwordAt else candidates.size)
                .lastOrNull { it.kind == Kind.USERNAME }
                ?: candidates.firstOrNull { it.kind == Kind.USERNAME }
                ?: candidates.take(if (passwordAt >= 0) passwordAt else 0).lastOrNull { it.kind == Kind.TEXT }

            return FillableFields(
                usernameId = username?.id,
                passwordId = password?.id,
                webDomain = webDomain,
                usernameValue = username?.value.orEmpty(),
                passwordValue = password?.value.orEmpty(),
            )
        }

        private fun visit(node: AssistStructure.ViewNode, onNode: (AssistStructure.ViewNode) -> Unit) {
            onNode(node)
            for (i in 0 until node.childCount) visit(node.getChildAt(i), onNode)
        }

        private fun kindOf(node: AssistStructure.ViewNode): Kind {
            node.autofillHints?.forEach { hint ->
                when (hint.lowercase()) {
                    "password", "newpassword", "new-password", "current-password" -> return Kind.PASSWORD
                    "username", "emailaddress", "email", "tel", "phone" -> return Kind.USERNAME
                }
            }

            val html = node.htmlInfo
            if (html?.tag.equals("input", ignoreCase = true)) {
                val attributes = html?.attributes?.associate { it.first.lowercase() to it.second.orEmpty().lowercase() } ?: emptyMap()
                when (attributes["type"]) {
                    "password" -> return Kind.PASSWORD
                    "email", "tel" -> return Kind.USERNAME
                }
                val named = listOfNotNull(attributes["name"], attributes["id"], attributes["autocomplete"]).joinToString(" ")
                nameKind(named)?.let { return it }
            }

            val variation = node.inputType and InputType.TYPE_MASK_VARIATION
            val isText = (node.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
            if (isText && variation in setOf(
                    InputType.TYPE_TEXT_VARIATION_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                )
            ) {
                return Kind.PASSWORD
            }
            if (isText && variation in setOf(
                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                    InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                )
            ) {
                return Kind.USERNAME
            }

            val named = listOfNotNull(node.idEntry, node.hint, node.text?.toString()).joinToString(" ")
            nameKind(named)?.let { return it }

            return if (node.className?.contains("EditText") == true || node.htmlInfo != null) Kind.TEXT else Kind.OTHER
        }

        /** What a field's own name says it holds, for pages that set no hints at all. */
        private fun nameKind(named: String): Kind? {
            val n = named.lowercase()
            if (n.isBlank()) return null
            if (Regex("pass(word|wd|phrase)?|pwd|pin\\b").containsMatchIn(n)) return Kind.PASSWORD
            if (Regex("user|email|e-mail|login|signin|sign-in|account|mobile|phone|userid|uid").containsMatchIn(n)) return Kind.USERNAME
            return null
        }
    }

    private data class Candidate(val id: AutofillId, val kind: Kind, val value: String)

    private enum class Kind { PASSWORD, USERNAME, TEXT, OTHER }
}
