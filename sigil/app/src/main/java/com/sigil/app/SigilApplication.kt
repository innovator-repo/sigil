package com.sigil.app

import android.app.Application
import com.sigil.app.data.Repository
import com.sigil.app.data.SigilDatabase
import com.sigil.app.security.SecretVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SigilApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository: Repository by lazy {
        Repository(SigilDatabase.get(this), SecretVault())
    }

    override fun onCreate() {
        super.onCreate()
        appScope.launch { repository.seedIfEmpty() }
    }
}
