package pl.godzinypracy.workly.sync

import android.accounts.Account
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import pl.godzinypracy.workly.data.WorkBackupCodec
import pl.godzinypracy.workly.data.WorkRepository
import java.io.IOException
import java.util.concurrent.TimeUnit

class GoogleDriveSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : Worker(appContext, workerParameters) {

    override fun doWork(): Result {
        val store = GoogleDriveSyncStore(applicationContext)
        val state = store.load()
        val accountEmail = state.accountEmail
        if (!state.enabled || accountEmail.isNullOrBlank()) return Result.success()

        store.markSyncing()
        return try {
            val request = AuthorizationRequest.builder()
                .setAccount(Account(accountEmail, GOOGLE_ACCOUNT_TYPE))
                .setRequestedScopes(listOf(Scope(DriveAppDataClient.DRIVE_APPDATA_SCOPE)))
                .build()
            val authorization = Tasks.await(
                Identity.getAuthorizationClient(applicationContext).authorize(request),
                30,
                TimeUnit.SECONDS
            )
            val token = authorization.accessToken
            if (authorization.hasResolution() || token.isNullOrBlank()) {
                store.markNeedsReconnect("Połącz ponownie konto Google, aby wznowić kopie.")
                return Result.failure()
            }

            val payload = WorkBackupCodec.encode(WorkRepository(applicationContext))
            val driveClient = DriveAppDataClient(token)
            driveClient.uploadBackup(payload)
            runCatching { driveClient.getAccountInfo() }
                .getOrNull()
                ?.let { accountInfo ->
                    store.saveAccountProfile(accountEmail, accountInfo.photoUrl)
                }
            store.markSynced()
            Result.success()
        } catch (error: DriveHttpException) {
            when {
                error.statusCode == 401 || error.statusCode == 403 -> {
                    store.markNeedsReconnect("Google wymaga ponownego potwierdzenia dostępu.")
                    Result.failure()
                }
                error.statusCode == 429 || error.statusCode >= 500 -> retryOrFail(store)
                else -> {
                    store.markError("Nie udało się zapisać kopii Google Drive.")
                    Result.failure()
                }
            }
        } catch (_: IOException) {
            retryOrFail(store)
        } catch (_: Exception) {
            store.markError("Nie udało się wykonać kopii zapasowej.")
            Result.failure()
        }
    }

    private fun retryOrFail(store: GoogleDriveSyncStore): Result {
        return if (runAttemptCount < MAX_RETRY_COUNT) {
            store.markError("Kopia oczekuje na stabilne połączenie z internetem.")
            Result.retry()
        } else {
            store.markError("Synchronizacja nie powiodła się. Spróbuj ponownie później.")
            Result.failure()
        }
    }

    companion object {
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
        private const val MAX_RETRY_COUNT = 5
    }
}

object GoogleDriveSyncScheduler {
    private const val IMMEDIATE_WORK_NAME = "workly-google-drive-sync"
    private const val PERIODIC_WORK_NAME = "workly-google-drive-periodic-sync"

    private val connectedNetworkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun enqueueNow(context: Context) {
        if (!GoogleDriveSyncStore(context).load().enabled) return
        val request = OneTimeWorkRequestBuilder<GoogleDriveSyncWorker>()
            .setConstraints(connectedNetworkConstraints)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun schedulePeriodic(context: Context) {
        if (!GoogleDriveSyncStore(context).load().enabled) return
        val request = PeriodicWorkRequestBuilder<GoogleDriveSyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(connectedNetworkConstraints)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(IMMEDIATE_WORK_NAME)
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(PERIODIC_WORK_NAME)
    }
}
