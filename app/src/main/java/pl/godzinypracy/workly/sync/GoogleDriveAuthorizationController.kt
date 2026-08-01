package pl.godzinypracy.workly.sync

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope

class GoogleDriveAuthorizationController(context: Context) {
    private val authorizationClient = Identity.getAuthorizationClient(context)
    private var onAuthorized: ((AuthorizationResult) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    fun authorize(
        accountEmail: String?,
        onResolutionRequired: (PendingIntent) -> Unit,
        onAuthorized: (AuthorizationResult) -> Unit,
        onError: (String) -> Unit
    ) {
        this.onAuthorized = onAuthorized
        this.onError = onError
        val requestBuilder = AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(
                    Scope(DriveAppDataClient.DRIVE_APPDATA_SCOPE)
                )
            )
        if (accountEmail.isNullOrBlank()) {
            requestBuilder.setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
        } else {
            requestBuilder.setAccount(Account(accountEmail, GOOGLE_ACCOUNT_TYPE))
        }

        authorizationClient.authorize(requestBuilder.build())
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    result.pendingIntent?.let(onResolutionRequired)
                        ?: fail("Nie udało się otworzyć wyboru konta Google.")
                } else {
                    complete(result)
                }
            }
            .addOnFailureListener {
                fail("Nie udało się połączyć z kontem Google.")
            }
    }

    fun handleResolution(data: Intent?) {
        if (data == null) {
            fail("Anulowano łączenie z kontem Google.")
            return
        }
        runCatching { authorizationClient.getAuthorizationResultFromIntent(data) }
            .onSuccess(::complete)
            .onFailure { fail("Nie udzielono dostępu do kopii Google Drive.") }
    }

    fun revoke(
        accountEmail: String,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        val request = RevokeAccessRequest.builder()
            .setAccount(Account(accountEmail, GOOGLE_ACCOUNT_TYPE))
            .setScopes(
                listOf(
                    Scope(DriveAppDataClient.DRIVE_APPDATA_SCOPE)
                )
            )
            .build()
        authorizationClient.revokeAccess(request)
            .addOnSuccessListener { onComplete() }
            .addOnFailureListener { onError("Kopia została usunięta, ale nie udało się cofnąć zgody Google.") }
    }

    private fun complete(result: AuthorizationResult) {
        val callback = onAuthorized
        clearCallbacks()
        callback?.invoke(result)
    }

    private fun fail(message: String) {
        val callback = onError
        clearCallbacks()
        callback?.invoke(message)
    }

    private fun clearCallbacks() {
        onAuthorized = null
        onError = null
    }

    companion object {
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}
