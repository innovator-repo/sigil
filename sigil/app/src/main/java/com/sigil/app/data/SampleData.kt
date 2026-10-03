package com.sigil.app.data

import com.sigil.app.data.model.BlockType
import com.sigil.app.data.model.CardItem
import com.sigil.app.data.model.LoginItem
import com.sigil.app.data.model.NoteBlock
import com.sigil.app.data.model.NoteItem
import com.sigil.app.data.model.Segment
import com.sigil.app.data.model.Space
import com.sigil.app.data.model.WifiItem

/**
 * The same example content the Kept prototype ships with (fictional - not anyone's real
 * accounts), so a fresh install of Sigil demonstrates every surface immediately.
 */
object SampleData {
    private const val HOUR = 3_600_000L
    private const val DAY = 86_400_000L

    private fun text(s: String) = Segment.Text(s)
    private var blockSeq = 0
    private fun block(vararg segs: Segment, type: BlockType = BlockType.PARAGRAPH, done: Boolean = false) =
        NoteBlock(id = "sb-${blockSeq++}", type = type, done = done, segments = segs.toList())

    suspend fun seed(repo: Repository) {
        val now = System.currentTimeMillis()

        suspend fun seal(plain: String) = repo.sealValue(plain)

        // n1 - Lake house weekend (pinned, family)
        val wifiSealId = seal("Maple-harbor-quiet7")
        val doorSealId = seal("4821#")
        repo.saveNote(
            NoteItem(
                id = "n1", space = Space.FAMILY, pinned = true, updated = now - 2 * HOUR,
                blocks = listOf(
                    block(text("Lake house weekend")),
                    block(text("Fri 17 – Sun 19 Oct · 14 Fernhill Road")),
                    block(text("Wi‑Fi Fernhill-5G, password "), Segment.Seal(wifiSealId, "Wi‑Fi password")),
                    block(text("Door code "), Segment.Seal(doorSealId, "Door code")),
                    block(text("Firewood"), type = BlockType.CHECKLIST, done = true),
                    block(text("Oat milk, eggs, basil"), type = BlockType.CHECKLIST),
                    block(text("Board games"), type = BlockType.CHECKLIST),
                    block(text("Charge the kayak pump"), type = BlockType.CHECKLIST),
                ),
            ),
        )

        // l1 - Google (root account, password reused by Airbnb and Dropbox - each has its own copy)
        val summerPw = seal("Summer2019!")
        repo.saveLogin(
            LoginItem(
                id = "l1", space = Space.PERSONAL, updated = now - 210 * DAY,
                title = "Google", username = "alex.morgan@gmail.com", passwordSecretId = summerPw,
                url = "accounts.google.com", isRoot = true,
            ),
        )

        // n2 - Project Aurora launch notes (work)
        val apiKeySeal = seal("sk_test_51HxYzAbCdEfGhIjKlMn")
        repo.saveNote(
            NoteItem(
                id = "n2", space = Space.WORK, updated = now - 26 * HOUR,
                blocks = listOf(
                    block(text("Project Aurora launch notes")),
                    block(text("Launch window moved to Tuesday. Staging looks good; the onboarding copy still needs a pass.")),
                    block(text("Staging API key "), Segment.Seal(apiKeySeal, "Key")),
                    block(text("Update the status page copy"), type = BlockType.CHECKLIST),
                    block(text("Tell support about the new sign-in flow"), type = BlockType.CHECKLIST, done = true),
                ),
            ),
        )

        // l2 - GitHub (passkey + TOTP)
        val githubPw = seal("hoqfaz-Nemty4-vuxbef")
        val githubTotp = seal("JBSWY3DPEHPK3PXP")
        repo.saveLogin(
            LoginItem(
                id = "l2", space = Space.PERSONAL, updated = now - 9 * DAY,
                title = "GitHub", username = "alexmorgan", passwordSecretId = githubPw,
                url = "github.com", totpSecretId = githubTotp, hasPasskey = true,
            ),
        )

        // n3 - Thoughts on the move (personal)
        repo.saveNote(
            NoteItem(
                id = "n3", space = Space.PERSONAL, updated = now - 3 * DAY,
                blocks = listOf(
                    block(text("Thoughts on the move")),
                    block(text("What if the small room became the studio? Morning light, and no desk against the window.")),
                    block(text("Ask Sam about the shelving from the old flat.")),
                ),
            ),
        )

        // l3 - Netflix (leaked, reused password)
        val netflixPw = seal("bingewatch-88")
        repo.saveLogin(
            LoginItem(
                id = "l3", space = Space.PERSONAL, updated = now - 400 * DAY,
                title = "Netflix", username = "alex.morgan@gmail.com", passwordSecretId = netflixPw,
                url = "netflix.com", leaked = true,
            ),
        )

        // w1 - Home Wi-Fi (pinned, family)
        val homeWifiPw = seal("Otter-velvet-canyon4-drum")
        repo.saveWifi(
            WifiItem(
                id = "w1", space = Space.FAMILY, pinned = true, updated = now - 120 * DAY,
                title = "Home Wi\u2011Fi", network = "Morgan-Home", passwordSecretId = homeWifiPw, security = "WPA3 Personal",
            ),
        )

        // n4 - Passport renewal (personal)
        val passportSeal = seal("K4829175")
        repo.saveNote(
            NoteItem(
                id = "n4", space = Space.PERSONAL, updated = now - 6 * DAY,
                blocks = listOf(
                    block(text("Passport renewal")),
                    block(text("Photos done. Form filled in online; the old passport still needs posting.")),
                    block(text("Passport number "), Segment.Seal(passportSeal, "Passport number")),
                ),
            ),
        )

        // l4 - Northwind Credit Union (passkey)
        val northwindPw = seal("fipzyb-7Yawmo-qunpaq")
        repo.saveLogin(
            LoginItem(
                id = "l4", space = Space.PERSONAL, updated = now - 30 * DAY,
                title = "Northwind Credit Union", username = "alexm", passwordSecretId = northwindPw,
                url = "northwind.example", hasPasskey = true,
            ),
        )

        // l5 - Figma (supports 2FA, not yet on)
        val figmaPw = seal("Kettle-orbit-meadow5-lynx")
        repo.saveLogin(
            LoginItem(
                id = "l5", space = Space.WORK, updated = now - 60 * DAY,
                title = "Figma", username = "alex@morgan.studio", passwordSecretId = figmaPw,
                url = "figma.com", supports2FA = true,
            ),
        )

        // n5 - Reading: The Extended Mind (personal)
        repo.saveNote(
            NoteItem(
                id = "n5", space = Space.PERSONAL, updated = now - 12 * DAY,
                blocks = listOf(
                    block(text("Reading: The Extended Mind")),
                    block(text("“The notebook plays for Otto the same role that memory plays for Inga.” Clark & Chalmers, 1998.")),
                    block(text("So a notes app isn't storage. It's part of someone's mind, and it should be designed with that respect.")),
                ),
            ),
        )

        // c1 - Everyday Visa (personal)
        val cardNumberSeal = seal("4242 4242 4242 4242")
        val cardCodeSeal = seal("314")
        repo.saveCard(
            CardItem(
                id = "c1", space = Space.PERSONAL, updated = now - 70 * DAY,
                title = "Everyday Visa", holder = "Alex Morgan", numberSecretId = cardNumberSeal,
                last4 = "4242", expiry = "08/29", codeSecretId = cardCodeSeal,
            ),
        )

        // l6 - Airbnb (reused password)
        repo.saveLogin(
            LoginItem(
                id = "l6", space = Space.PERSONAL, updated = now - 300 * DAY,
                title = "Airbnb", username = "alex.morgan@gmail.com", passwordSecretId = seal("Summer2019!"),
                url = "airbnb.com",
            ),
        )

        // n6 - Mira's birthday ideas (family)
        repo.saveNote(
            NoteItem(
                id = "n6", space = Space.FAMILY, updated = now - 20 * DAY,
                blocks = listOf(
                    block(text("Mira's birthday ideas")),
                    block(text("Pottery class for two. The blue scarf from the Saturday market. Tickets for the lighthouse tour?")),
                ),
            ),
        )

        // l7 - Dropbox (reused password)
        repo.saveLogin(
            LoginItem(
                id = "l7", space = Space.PERSONAL, updated = now - 500 * DAY,
                title = "Dropbox", username = "alex.morgan@gmail.com", passwordSecretId = seal("Summer2019!"),
                url = "dropbox.com",
            ),
        )

        // l8 - Spotify
        val spotifyPw = seal("Lemon-cobalt-harbor8-quilt")
        repo.saveLogin(
            LoginItem(
                id = "l8", space = Space.PERSONAL, updated = now - 90 * DAY,
                title = "Spotify", username = "alex.morgan@gmail.com", passwordSecretId = spotifyPw,
                url = "spotify.com",
            ),
        )

        // n7 - Car (personal)
        val garageSeal = seal("7302")
        repo.saveNote(
            NoteItem(
                id = "n7", space = Space.PERSONAL, updated = now - 45 * DAY,
                blocks = listOf(
                    block(text("Car")),
                    block(text("Service due at 60,000 km. Tyres rotated in March.")),
                    block(text("Garage door "), Segment.Seal(garageSeal, "Door code")),
                ),
            ),
        )

        // n8 - Old router settings (deleted, personal)
        repo.saveNote(
            NoteItem(
                id = "n8", space = Space.PERSONAL, updated = now - 50 * DAY, deletedAt = now - 3 * DAY,
                blocks = listOf(
                    block(text("Old router settings")),
                    block(text("Replaced by the new mesh system in August.")),
                ),
            ),
        )
    }
}
