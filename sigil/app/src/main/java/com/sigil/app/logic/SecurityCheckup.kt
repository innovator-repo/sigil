package com.sigil.app.logic

import com.sigil.app.data.Repository
import com.sigil.app.data.model.LoginItem

enum class IssueType { LEAKED, REUSED, NO_2FA }

data class SecurityIssue(
    val key: String,
    val type: IssueType,
    val item: LoginItem,
    val others: List<LoginItem> = emptyList(),
    val priority: Int,
)

data class CheckupResult(val open: List<SecurityIssue>, val total: Int, val healthy: Int)

data class IssueCopy(val kind: String, val why: String, val fix: String, val action: String, val soft: Boolean)

/**
 * The "Check-up" security scan: reused passwords, passwords found in a leak, and logins
 * that support 2FA but don't have it on. Ported from the web prototype's issues()/issueCopy().
 * Password values are decrypted only transiently, to compare them - never displayed here.
 */
object SecurityCheckup {

    suspend fun compute(logins: List<LoginItem>, repo: Repository, snoozedOrder: List<String>): CheckupResult {
        // Items with no main password (a license key, a lock code) have nothing to compare.
        val active = logins.filter { !it.isDeleted && it.hasPassword }
        val valueOf = HashMap<String, String>(active.size)
        for (l in active) valueOf[l.id] = repo.revealValue(l.passwordSecretId)
        val groups = active.groupBy { valueOf[it.id] }

        val out = ArrayList<SecurityIssue>()
        for (l in active) {
            val importance = if (l.isRoot) 3 else 1
            if (l.leaked) out.add(SecurityIssue("leaked:${l.id}", IssueType.LEAKED, l, priority = 3 * importance))
            val others = groups[valueOf[l.id]].orEmpty().filter { it.id != l.id }
            if (others.isNotEmpty()) out.add(SecurityIssue("reused:${l.id}", IssueType.REUSED, l, others, priority = 2 * importance))
            if (l.supports2FA && l.totpSecretId == null && !l.hasPasskey) {
                out.add(SecurityIssue("no2fa:${l.id}", IssueType.NO_2FA, l, priority = importance))
            }
        }
        out.sortWith(compareByDescending<SecurityIssue> { it.priority }.thenBy { it.item.title })

        val snoozed = out.filter { snoozedOrder.contains(it.key) }.sortedBy { snoozedOrder.indexOf(it.key) }
        val open = out.filterNot { snoozedOrder.contains(it.key) } + snoozed
        val flagged = open.map { it.item.id }.toSet()
        return CheckupResult(open, active.size, active.count { it.id !in flagged })
    }

    private fun names(items: List<LoginItem>): String {
        val n = items.map { it.title }
        return if (n.size == 1) n[0] else "${n.dropLast(1).joinToString(", ")} and ${n.last()}"
    }

    fun describe(issue: SecurityIssue): IssueCopy {
        val t = issue.item.title
        return when (issue.type) {
            IssueType.REUSED -> {
                val n = issue.others.size
                val rootNote = if (issue.item.isRoot) " Your email can reset every other password, so this one comes first." else ""
                IssueCopy(
                    kind = "Reused password",
                    why = "This password is also used for ${names(issue.others)}. If one of them leaks, all ${n + 1} can be opened.$rootNote",
                    fix = "Give $t its own password. It takes about a minute.",
                    action = "Change Password",
                    soft = false,
                )
            }
            IssueType.LEAKED -> IssueCopy(
                kind = "Found in a data leak",
                why = "This password appeared in a known data leak, so it's on the lists attackers try first.",
                fix = "Change it on ${issue.item.url}. Sigil suggests a new one and saves it for you.",
                action = "Change Password",
                soft = false,
            )
            IssueType.NO_2FA -> IssueCopy(
                kind = "Verification codes available",
                why = "$t can ask for a code when someone signs in on a new device.",
                fix = "Turn on codes and Sigil fills them in for you, like a password.",
                action = "Turn On Codes",
                soft = true,
            )
        }
    }
}
