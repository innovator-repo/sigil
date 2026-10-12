package com.sigil.app.autofill

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.sigil.app.R
import com.sigil.app.SigilApplication
import com.sigil.app.data.model.Category
import com.sigil.app.data.model.LoginItem
import com.sigil.app.ui.theme.SigilTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The unlock step behind every autofill row. Autofill shows Sigil's rows with nothing in them;
 * choosing one starts this activity, which asks for presence and only then decrypts the password
 * and hands the finished dataset back to the field that asked.
 *
 * Started without an item it is the "Choose from Sigil" row instead: the same unlock, then the
 * list of saved logins to pick from, for a site saved under a different address.
 */
class AutofillFillActivity : FragmentActivity() {

    private val app: SigilApplication get() = applicationContext as SigilApplication

    private lateinit var fields: FillableFields
    private var itemId: String? = null
    private var where: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A password is about to be on screen, so keep this window out of screenshots.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        fields = FillableFields(
            usernameId = intent.parcelable(EXTRA_USERNAME_ID),
            passwordId = intent.parcelable(EXTRA_PASSWORD_ID),
        )
        itemId = intent.getStringExtra(EXTRA_ITEM_ID)
        where = intent.getStringExtra(EXTRA_WHERE).orEmpty()

        if (!fields.canFill) {
            cancel()
            return
        }

        requirePresence {
            val chosen = itemId
            if (chosen != null) fillWith(chosen) else showPicker()
        }
    }

    /** Fingerprint, face or the screen lock, exactly as revealing a password in the app does. */
    private fun requirePresence(onPresent: () -> Unit) {
        val reason = if (where.isBlank()) getString(R.string.autofill_unlock_generic) else getString(R.string.autofill_unlock_for, where)
        if (BiometricManager.from(this).canAuthenticate(AUTHENTICATORS) != BiometricManager.BIOMETRIC_SUCCESS) {
            // No screen lock on the phone: there is nothing to check against, as in the app.
            onPresent()
            return
        }
        BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onPresent()

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = cancel()
            },
        ).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(reason)
                .setSubtitle("Sigil")
                .setAllowedAuthenticators(AUTHENTICATORS)
                .setConfirmationRequired(false)
                .build(),
        )
    }

    private fun fillWith(loginId: String) {
        lifecycleScope.launch {
            val login = withContext(Dispatchers.IO) { logins().firstOrNull { it.id == loginId } }
            if (login == null) {
                cancel()
                return@launch
            }
            val password = withContext(Dispatchers.IO) { app.repository.revealValue(login.passwordSecretId) }
            val dataset = Dataset.Builder().apply {
                val presentation = row(login.title, login.username.ifBlank { where })
                fields.usernameId?.let { setValue(it, AutofillValue.forText(login.username), presentation) }
                fields.passwordId?.let { setValue(it, AutofillValue.forText(password), presentation) }
            }.build()
            setResult(RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset))
            finish()
        }
    }

    private fun showPicker() {
        lifecycleScope.launch {
            val all = withContext(Dispatchers.IO) { logins() }
            setContent {
                SigilTheme {
                    LoginPicker(all, where) { fillWith(it.id) }
                }
            }
        }
    }

    private suspend fun logins(): List<LoginItem> =
        app.repository.observeItems().first()
            .filterIsInstance<LoginItem>()
            .filter { !it.isDeleted && it.hasPassword && it.category != Category.LOCK }
            .sortedByDescending { it.updated }

    private fun row(title: String, subtitle: String): RemoteViews =
        RemoteViews(packageName, R.layout.autofill_row).apply {
            setTextViewText(R.id.autofill_row_title, title)
            setTextViewText(R.id.autofill_row_subtitle, subtitle)
        }

    private fun cancel() {
        setResult(RESULT_CANCELED)
        finish()
    }

    private inline fun <reified T : Any> Intent.parcelable(name: String): T? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableExtra(name, T::class.java)
        else @Suppress("DEPRECATION") getParcelableExtra(name) as? T

    companion object {
        private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        private const val EXTRA_USERNAME_ID = "sigil.autofill.username_id"
        private const val EXTRA_PASSWORD_ID = "sigil.autofill.password_id"
        private const val EXTRA_ITEM_ID = "sigil.autofill.item_id"
        private const val EXTRA_WHERE = "sigil.autofill.where"

        /** [itemId] null means the person picks a login here instead of Sigil choosing one. */
        fun intent(context: Context, fields: FillableFields, itemId: String?, where: String): Intent =
            Intent(context, AutofillFillActivity::class.java)
                .putExtra(EXTRA_USERNAME_ID, fields.usernameId as? AutofillId)
                .putExtra(EXTRA_PASSWORD_ID, fields.passwordId as? AutofillId)
                .putExtra(EXTRA_ITEM_ID, itemId)
                .putExtra(EXTRA_WHERE, where)
    }
}

@Composable
private fun LoginPicker(logins: List<LoginItem>, where: String, onPick: (LoginItem) -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
            Text(
                text = if (where.isBlank()) "Fill a login" else "Fill a login for $where",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            LazyColumn {
                items(logins, key = { it.id }) { login ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(login) }
                            .padding(vertical = 14.dp),
                    ) {
                        Text(login.title, style = MaterialTheme.typography.bodyLarge)
                        val subtitle = login.username.ifBlank { login.url }.ifBlank { login.category.label }
                        if (subtitle.isNotBlank()) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}
