package com.sigil.app.autofill

import android.app.PendingIntent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.widget.RemoteViews
import com.sigil.app.R
import com.sigil.app.SigilApplication
import com.sigil.app.data.model.Category
import com.sigil.app.data.model.LoginItem
import com.sigil.app.data.model.Space
import com.sigil.core.DomainMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Offers Sigil's logins wherever Android asks for a password: a browser's sign-in page, or an
 * app's own login screen. Turn it on once in Settings → Passwords & accounts → autofill service,
 * and from then on tapping a user name box shows the logins Sigil has for that site.
 *
 * Nothing is decrypted here. Each row is an empty dataset with an authentication intent, so the
 * values only ever come from [AutofillFillActivity] after a fingerprint, face or screen-lock
 * check - the same "presence, not passwords" rule as revealing a password inside the app.
 */
class SigilAutofillService : AutofillService() {

    private val app: SigilApplication get() = applicationContext as SigilApplication

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        val fields = structure?.let { FillableFields.parse(it) }
        if (fields == null || !fields.canFill) {
            callback.onSuccess(null)
            return
        }

        val askingPackage = structure.activityComponent?.packageName.orEmpty()
        // Never offer Sigil's own logins back to Sigil: the app has its own list.
        if (askingPackage == packageName) {
            callback.onSuccess(null)
            return
        }

        val logins = loadLogins()
        val matches = logins.filter { login ->
            if (fields.webDomain.isNotEmpty()) DomainMatch.matchesDomain(login.url, fields.webDomain)
            else DomainMatch.matchesPackage(login.url, login.title, askingPackage)
        }

        val response = FillResponse.Builder()
        val where = fields.webDomain.ifEmpty { askingPackage }
        matches.take(MAX_ROWS).forEach { login ->
            response.addDataset(lockedDataset(fields, login.id, login.title, login.username.ifBlank { "No user name" }, where))
        }
        // Even with no match, one row opens Sigil to pick any login - the manual choice the
        // person would otherwise make by leaving the page and copying the password across.
        if (matches.isEmpty() && logins.isNotEmpty()) {
            response.addDataset(lockedDataset(fields, itemId = null, title = "Choose from Sigil", subtitle = "Search all logins", where = where))
        }

        if (fields.canSave || fields.autofillIds.isNotEmpty()) {
            response.setSaveInfo(
                SaveInfo.Builder(SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD, fields.autofillIds)
                    .setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
                    .build(),
            )
        }

        callback.onSuccess(response.build())
    }

    /**
     * Saves a login the person just typed somewhere else, once Android's own "Save password?"
     * bar is accepted. New items land in Personal as a plain Login, titled after the site.
     */
    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        val fields = structure?.let { FillableFields.parse(it) }
        if (fields == null || !fields.canSave) {
            callback.onFailure(getString(R.string.autofill_nothing_to_save))
            return
        }
        val where = fields.webDomain.ifEmpty { structure.activityComponent?.packageName.orEmpty() }
        val title = DomainMatch.registrableDomain(where).ifEmpty { where }.ifEmpty { "Login" }
        app.appScope.launch(Dispatchers.IO) {
            runCatching {
                app.repository.createLogin(
                    space = Space.PERSONAL,
                    title = title.replaceFirstChar { it.uppercase() },
                    username = fields.usernameValue,
                    url = fields.webDomain,
                    passwordPlaintext = fields.passwordValue,
                )
            }.onSuccess { callback.onSuccess() }
                .onFailure { callback.onFailure(getString(R.string.autofill_could_not_save)) }
        }
    }

    /** A row in the autofill menu with no values in it yet, and the unlock screen behind it. */
    private fun lockedDataset(fields: FillableFields, itemId: String?, title: String, subtitle: String, where: String): Dataset {
        val dataset = Dataset.Builder()
        val presentation = row(title, subtitle)
        fields.usernameId?.let { dataset.setValue(it, null, presentation) }
        fields.passwordId?.let { dataset.setValue(it, null, presentation) }
        dataset.setAuthentication(authentication(fields, itemId, where).intentSender)
        return dataset.build()
    }

    private fun authentication(fields: FillableFields, itemId: String?, where: String): PendingIntent {
        val intent = AutofillFillActivity.intent(this, fields, itemId, where)
        // The intent has to stay mutable: autofill writes the chosen dataset back into it.
        var flags = PendingIntent.FLAG_CANCEL_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getActivity(this, requestCode++, intent, flags)
    }

    private fun row(title: String, subtitle: String): RemoteViews =
        RemoteViews(packageName, R.layout.autofill_row).apply {
            setTextViewText(R.id.autofill_row_title, title)
            setTextViewText(R.id.autofill_row_subtitle, subtitle)
        }

    /**
     * Autofill's callbacks are synchronous and the menu has to appear while the person is still
     * looking at the field, so the item list is read here and then waited for, briefly. Only
     * titles, user names and URLs are read; passwords stay sealed in the database.
     */
    private fun loadLogins(): List<LoginItem> = runBlocking {
        withTimeoutOrNull(LOAD_TIMEOUT_MS) {
            app.repository.observeItems().first()
                .filterIsInstance<LoginItem>()
                .filter { !it.isDeleted && it.hasPassword && it.category != Category.LOCK }
                .sortedByDescending { it.updated }
        }.orEmpty()
    }

    private var requestCode = 1000

    private companion object {
        const val MAX_ROWS = 8
        const val LOAD_TIMEOUT_MS = 2_000L
    }
}
