package com.sigil.core

/**
 * Decides whether a saved login belongs to the site (or app) asking to be filled.
 *
 * Android hands an autofill request a web domain like `accounts.google.com`, while a saved
 * login's URL is whatever was typed when it was saved: `https://google.com/signin`,
 * `mail.google.com`, or nothing at all. Both sides are reduced to the part people think of
 * as "the site" - the registrable domain, so `google.com` for both of the above - and a
 * request matches when those agree.
 */
object DomainMatch {

    /**
     * Second-level suffixes that are really registries, so `bbc.co.uk` keeps three labels
     * instead of collapsing to `co.uk`. Not the full public suffix list: just the ones a
     * phone in India is likely to meet, which is where Sigil's logins come from.
     */
    private val twoLevelSuffixes = setOf(
        "co.uk", "org.uk", "ac.uk", "gov.uk", "me.uk", "net.uk",
        "co.in", "net.in", "org.in", "gov.in", "ac.in", "edu.in", "res.in", "nic.in", "firm.in", "gen.in", "ind.in",
        "com.au", "net.au", "org.au", "edu.au", "gov.au",
        "co.jp", "or.jp", "ne.jp", "ac.jp", "go.jp",
        "com.br", "com.cn", "com.mx", "com.sg", "com.my", "com.hk", "com.tw", "com.tr",
        "co.nz", "co.za", "co.kr", "co.id", "co.th", "com.ph", "com.pk", "com.bd", "com.np",
        "com.ar", "com.co", "com.pe", "com.sa", "com.eg", "com.ng", "com.ua",
    )

    /** Strips scheme, credentials, port, path and a leading `www.`, leaving a bare host. */
    fun host(urlOrHost: String): String {
        var s = urlOrHost.trim().lowercase()
        if (s.isEmpty()) return ""
        s = s.substringAfter("://")
        s = s.substringBefore('/').substringBefore('?').substringBefore('#')
        s = s.substringAfterLast('@')
        // An IPv6 host keeps its brackets; anything else loses a :port.
        if (!s.startsWith("[")) s = s.substringBefore(':')
        s = s.trim('.')
        return s.removePrefix("www.")
    }

    /** The registrable domain of a host: `mail.google.com` and `google.com` both give `google.com`. */
    fun registrableDomain(urlOrHost: String): String {
        val host = host(urlOrHost)
        if (host.isEmpty()) return ""
        val labels = host.split('.')
        if (labels.size < 3) return host
        val lastTwo = labels.takeLast(2).joinToString(".")
        val keep = if (lastTwo in twoLevelSuffixes) 3 else 2
        if (labels.size <= keep) return host
        return labels.takeLast(keep).joinToString(".")
    }

    /**
     * True when a login saved for [savedUrl] is the one to offer on [requestDomain]. Both
     * are compared as registrable domains, so a login saved for the sign-in page of a site
     * is offered on the rest of it, and `amazon.in` is never offered on `amazon.com`.
     */
    fun matchesDomain(savedUrl: String, requestDomain: String): Boolean {
        val saved = registrableDomain(savedUrl)
        val asked = registrableDomain(requestDomain)
        return saved.isNotEmpty() && asked.isNotEmpty() && saved == asked
    }

    /**
     * True when a login looks like it belongs to the app with [packageName], for requests from
     * an app rather than a browser. Android has no verified link between the two, so this only
     * matches on the app's own name: `com.spotify.music` against a login saved for
     * `spotify.com` or titled "Spotify". It is a hint for ordering, not a claim of identity.
     */
    fun matchesPackage(savedUrl: String, savedTitle: String, packageName: String): Boolean {
        val words = packageName.lowercase().split('.')
            .filter { it.length >= 4 && it !in setOf("android", "com", "org", "net", "app", "apps", "mobile", "client") }
        if (words.isEmpty()) return false
        val savedName = registrableDomain(savedUrl).substringBefore('.')
        val title = savedTitle.lowercase().filter { it.isLetterOrDigit() }
        return words.any { w -> (savedName.isNotEmpty() && savedName == w) || (title.isNotEmpty() && title == w) }
    }
}
