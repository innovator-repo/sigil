package com.sigil.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DomainMatchTest {

    @Test
    fun hostDropsSchemePortPathAndWww() {
        assertEquals("google.com", DomainMatch.host("https://www.google.com/accounts/signin?next=1"))
        assertEquals("localhost", DomainMatch.host("http://localhost:8080"))
        assertEquals("irctc.co.in", DomainMatch.host("IRCTC.CO.IN"))
        assertEquals("example.com", DomainMatch.host("https://alex@example.com"))
        assertEquals("", DomainMatch.host("   "))
    }

    @Test
    fun registrableDomainKeepsTheSiteAndDropsSubdomains() {
        assertEquals("google.com", DomainMatch.registrableDomain("accounts.google.com"))
        assertEquals("google.com", DomainMatch.registrableDomain("https://mail.google.com/mail/u/0"))
        assertEquals("sbi.co.in", DomainMatch.registrableDomain("retail.onlinesbi.sbi.co.in"))
        assertEquals("bbc.co.uk", DomainMatch.registrableDomain("news.bbc.co.uk"))
        assertEquals("epfindia.gov.in", DomainMatch.registrableDomain("unifiedportal-mem.epfindia.gov.in"))
    }

    @Test
    fun loginSavedForASiteMatchesItsSignInSubdomain() {
        assertTrue(DomainMatch.matchesDomain("https://google.com", "accounts.google.com"))
        assertTrue(DomainMatch.matchesDomain("mail.google.com", "accounts.google.com"))
        assertTrue(DomainMatch.matchesDomain("https://www.irctc.co.in/nget/train-search", "www.irctc.co.in"))
    }

    @Test
    fun differentSitesNeverMatch() {
        assertFalse(DomainMatch.matchesDomain("amazon.in", "amazon.com"))
        assertFalse(DomainMatch.matchesDomain("google.com", "googie.com"))
        assertFalse(DomainMatch.matchesDomain("", "google.com"))
        assertFalse(DomainMatch.matchesDomain("google.com", ""))
        // A shared registry suffix is not a site.
        assertFalse(DomainMatch.matchesDomain("mybank.co.in", "otherbank.co.in"))
    }

    @Test
    fun appPackageMatchesOnTheAppsOwnName() {
        assertTrue(DomainMatch.matchesPackage("https://spotify.com", "Spotify", "com.spotify.music"))
        assertTrue(DomainMatch.matchesPackage("", "Groww", "com.nextbillion.groww"))
        assertFalse(DomainMatch.matchesPackage("https://spotify.com", "Spotify", "com.android.chrome"))
        assertFalse(DomainMatch.matchesPackage("", "", "com.spotify.music"))
    }
}
