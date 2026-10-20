package com.sigil.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.sigil.app.data.Repository
import com.sigil.app.ipc.Ecosystem
import com.sigil.app.data.SigilDatabase
import com.sigil.app.security.KeystoreVault
import com.sigil.app.security.SecretVault
import com.sigil.app.security.SensitiveClipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

open class SigilApplication : Application() {
    /** Outlives any screen, for work that must finish even if the activity goes away (clipboard clearing). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository: Repository by lazy { Repository(SigilDatabase.get(this), createVault()) }

    /** Overridden only by tests, which run where the Android Keystore doesn't exist. */
    protected open fun createVault(): SecretVault = KeystoreVault()
    val clipboard: SensitiveClipboard by lazy { SensitiveClipboard(this, appScope) { Ecosystem.logCheck(this, Ecosystem.ITEM_CLIPBOARD, true) } }
    val prefs: SharedPreferences by lazy { getSharedPreferences("sigil", Context.MODE_PRIVATE) }

    /** Completes once first-run seeding and startup housekeeping are done. */
    lateinit var startup: Job
        private set

    override fun onCreate() {
        super.onCreate()
        startup = appScope.launch(Dispatchers.IO) {
            if (!prefs.getBoolean(KEY_SEEDED, false)) {
                repository.seedSampleData()
                prefs.edit().putBoolean(KEY_SEEDED, true).apply()
            }
            repository.purgeExpired()
            repository.collectGarbage()
        }
    }

    companion object {
        private const val KEY_SEEDED = "seeded_sample_data"
        const val KEY_LOCKED = "locked"
    }
}
