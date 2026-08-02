package pl.godzinypracy.workly.update

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import pl.godzinypracy.workly.BuildConfig
import pl.godzinypracy.workly.R
import java.io.IOException
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

data class GitHubRelease(
    val tagName: String,
    val displayName: String,
    val pageUrl: String,
    val apkName: String,
    val apkDownloadUrl: String,
    val sha256DownloadUrl: String
)

class UpdatePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    val automaticChecksEnabled: Boolean
        get() = preferences.getBoolean(KEY_AUTOMATIC_CHECKS, false)

    fun setAutomaticChecksEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AUTOMATIC_CHECKS, enabled).apply()
    }

    fun wasAlreadyNotified(tagName: String): Boolean =
        preferences.getString(KEY_LAST_NOTIFIED_TAG, null) == tagName

    fun markNotified(tagName: String) {
        preferences.edit().putString(KEY_LAST_NOTIFIED_TAG, tagName).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "workly-update-preferences"
        const val KEY_AUTOMATIC_CHECKS = "automatic-checks-enabled"
        const val KEY_LAST_NOTIFIED_TAG = "last-notified-tag"
    }
}

class GitHubUpdateWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : Worker(appContext, workerParameters) {

    override fun doWork(): Result {
        val preferences = UpdatePreferences(applicationContext)
        val repository = BuildConfig.GITHUB_REPOSITORY.trim()

        if (!preferences.automaticChecksEnabled || repository.isBlank()) {
            return Result.success()
        }

        return try {
            val release = fetchLatestRelease(repository) ?: return Result.success()
            if (
                isNewerVersion(release.tagName, BuildConfig.VERSION_NAME) &&
                !preferences.wasAlreadyNotified(release.tagName)
            ) {
                val apkFile = UpdatePackageDownloader.downloadBlocking(
                    applicationContext,
                    release
                )
                val notificationShown = UpdateNotification.show(applicationContext, release, apkFile)
                if (notificationShown) preferences.markNotified(release.tagName)
            }
            Result.success()
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.failure()
        }
    }

    private fun fetchLatestRelease(repository: String): GitHubRelease? {
        val connection = (
            URL("https://api.github.com/repos/$repository/releases/latest")
                .openConnection() as HttpURLConnection
            ).apply {
            connectTimeout = 12_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "GodzinyPracy/${BuildConfig.VERSION_NAME}")
        }

        try {
            return when (val responseCode = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val payload = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(payload)
                    parseGitHubRelease(json)
                }

                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw IOException("GitHub Releases HTTP $responseCode")
            }
        } finally {
            connection.disconnect()
        }
    }
}

object AppUpdateScheduler {
    private const val PERIODIC_WORK_NAME = "workly-github-update-periodic"
    private const val IMMEDIATE_WORK_NAME = "workly-github-update-now"

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun enable(context: Context) {
        if (BuildConfig.GITHUB_REPOSITORY.isBlank()) return

        val periodicRequest = PeriodicWorkRequestBuilder<GitHubUpdateWorker>(
            12,
            TimeUnit.HOURS
        )
            .setInitialDelay(12, TimeUnit.HOURS)
            .setConstraints(networkConstraints)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )
        checkNow(context)
    }

    fun checkNow(context: Context) {
        if (BuildConfig.GITHUB_REPOSITORY.isBlank()) return

        val request = OneTimeWorkRequestBuilder<GitHubUpdateWorker>()
            .setConstraints(networkConstraints)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun disable(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
    }
}

private object UpdateNotification {
    private const val CHANNEL_ID = "app-updates"
    private const val NOTIFICATION_ID = 6001

    fun show(
        context: Context,
        release: GitHubRelease,
        apkFile: File
    ): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Aktualizacje aplikacji",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Informacje o nowych wersjach aplikacji Godziny Pracy"
            }
        )

        val installIntent = UpdatePackageInstaller.createInstallIntent(context, apkFile)
        val pendingIntent = PendingIntent.getActivity(
            context,
            release.tagName.hashCode(),
            installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle("Nowa wersja Godziny Pracy")
            .setContentText("Pobrano wersj\u0119 ${release.displayName}")
            .setStyle(
                Notification.BigTextStyle().bigText(
                    "Pobrano wersj\u0119 ${release.displayName}. Dotknij, aby uruchomi\u0107 instalacj\u0119."
                )
            )
            .setCategory(Notification.CATEGORY_STATUS)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
        return true
    }
}

private fun isNewerVersion(candidate: String, installed: String): Boolean {
    val candidateParts = candidate.toVersionParts()
    val installedParts = installed.toVersionParts()
    val length = maxOf(candidateParts.size, installedParts.size)

    repeat(length) { index ->
        val candidatePart = candidateParts.getOrElse(index) { 0 }
        val installedPart = installedParts.getOrElse(index) { 0 }
        if (candidatePart != installedPart) return candidatePart > installedPart
    }
    return false
}

private fun String.toVersionParts(): List<Int> {
    val normalized = trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore('-')

    return normalized.split('.').map { part ->
        part.takeWhile(Char::isDigit).toIntOrNull() ?: 0
    }
}
