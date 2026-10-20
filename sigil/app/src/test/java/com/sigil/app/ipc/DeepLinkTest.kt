package com.sigil.app.ipc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkTest {
    @Test fun knownLinks() {
        assertEquals(DeepLink.QUICK_NOTE, DeepLink.parse("sigil", "note", "/quick"))
        assertEquals(DeepLink.DIARY, DeepLink.parse("sigil", "note", "/diary"))
        assertEquals(DeepLink.SCRUB_CLIPBOARD, DeepLink.parse("sigil", "action", "/scrub-clipboard"))
    }

    @Test fun everythingElseIsIgnored() {
        assertNull(DeepLink.parse("https", "note", "/quick"))
        assertNull(DeepLink.parse("sigil", "note", "/other"))
        assertNull(DeepLink.parse("sigil", "seal", "/verify"))
        assertNull(DeepLink.parse(null, null, null))
    }
}
