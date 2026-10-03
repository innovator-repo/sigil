package com.sigil.app.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** An install from before extra fields existed must open with every login intact. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [34])
class MigrationTest {
    @Test fun version1LoginsSurviveAsPlainLogins() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val file = context.getDatabasePath("sigil.db").apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'v1')")
            db.execSQL("CREATE TABLE IF NOT EXISTS `secrets` (`id` TEXT NOT NULL, `ciphertext` BLOB NOT NULL, `iv` BLOB NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `notes` (`id` TEXT NOT NULL, `space` TEXT NOT NULL, `pinned` INTEGER NOT NULL, `updated` INTEGER NOT NULL, `deletedAt` INTEGER, `blocks_json` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `logins` (`id` TEXT NOT NULL, `space` TEXT NOT NULL, `pinned` INTEGER NOT NULL, `updated` INTEGER NOT NULL, `deletedAt` INTEGER, `title` TEXT NOT NULL, `username` TEXT NOT NULL, `password_secret_id` TEXT NOT NULL, `url` TEXT NOT NULL, `is_root` INTEGER NOT NULL, `totp_secret_id` TEXT, `has_passkey` INTEGER NOT NULL, `leaked` INTEGER NOT NULL, `supports_2fa` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `cards` (`id` TEXT NOT NULL, `space` TEXT NOT NULL, `pinned` INTEGER NOT NULL, `updated` INTEGER NOT NULL, `deletedAt` INTEGER, `title` TEXT NOT NULL, `holder` TEXT NOT NULL, `number_secret_id` TEXT NOT NULL, `last4` TEXT NOT NULL, `expiry` TEXT NOT NULL, `code_secret_id` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `wifi_networks` (`id` TEXT NOT NULL, `space` TEXT NOT NULL, `pinned` INTEGER NOT NULL, `updated` INTEGER NOT NULL, `deletedAt` INTEGER, `title` TEXT NOT NULL, `network` TEXT NOT NULL, `password_secret_id` TEXT NOT NULL, `security` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("INSERT INTO logins VALUES('l-1','PERSONAL',0,1,NULL,'IRCTC','me','s-1','irctc.co.in',0,NULL,0,0,0)")
            db.version = 1
        }

        val l = SigilDatabase.get(context).loginDao().all().single()
        assertEquals("IRCTC", l.title)
        assertEquals("LOGIN", l.category)
        assertEquals("[]", l.fieldsJson)
        assertEquals("", l.notes)
    }
}
