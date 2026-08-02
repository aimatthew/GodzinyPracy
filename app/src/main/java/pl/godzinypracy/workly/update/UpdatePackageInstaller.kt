package pl.godzinypracy.workly.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.godzinypracy.workly.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object UpdatePackageDownloader {
    suspend fun download(
        context: Context,
        release: GitHubRelease,
        onProgress: (Int) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        downloadBlocking(context, release, onProgress)
    }

    fun downloadBlocking(
        context: Context,
        release: GitHubRelease,
        onProgress: (Int) -> Unit = {}
    ): File {
        val expectedHash = downloadExpectedHash(release.sha256DownloadUrl)
        val updateDirectory = File(context.cacheDir, UPDATE_DIRECTORY).apply { mkdirs() }
        val safeName = release.apkName
            .substringAfterLast('/')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "GodzinyPracy-update.apk" }
        val target = File(updateDirectory, safeName)

        if (target.isFile && sha256(target).equals(expectedHash, ignoreCase = true)) {
            verifyPackageName(context, target)
            onProgress(100)
            return target
        }

        val temporary = File(updateDirectory, "$safeName.download.apk")
        temporary.delete()
        downloadFile(release.apkDownloadUrl, temporary, onProgress)

        val actualHash = sha256(temporary)
        if (!actualHash.equals(expectedHash, ignoreCase = true)) {
            temporary.delete()
            throw IOException("Suma SHA-256 pobranego APK jest nieprawid\u0142owa.")
        }

        verifyPackageName(context, temporary)
        updateDirectory.listFiles()
            ?.filter { it != temporary }
            ?.forEach { it.delete() }

        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
        return target
    }

    private fun downloadExpectedHash(url: String): String {
        val payload = openConnection(url).let { connection ->
            try {
                if (connection.responseCode !in 200..299) {
                    throw IOException("Pobieranie sumy SHA-256: HTTP ${connection.responseCode}")
                }
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }

        return SHA_256_REGEX.find(payload)?.value
            ?: throw IOException("W wydaniu brakuje prawid\u0142owej sumy SHA-256.")
    }

    private fun downloadFile(
        url: String,
        target: File,
        onProgress: (Int) -> Unit
    ) {
        val connection = openConnection(url)
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("Pobieranie APK: HTTP ${connection.responseCode}")
            }

            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            onProgress(
                                ((downloadedBytes * 100L) / totalBytes)
                                    .toInt()
                                    .coerceIn(0, 100)
                            )
                        }
                    }
                }
            }
            onProgress(100)
        } catch (error: Exception) {
            target.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 45_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", "GodzinyPracy/${BuildConfig.VERSION_NAME}")
        }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Suppress("DEPRECATION")
    private fun verifyPackageName(context: Context, apkFile: File) {
        val archiveInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            ?: throw IOException("Pobrany plik nie jest prawid\u0142owym APK.")
        if (archiveInfo.packageName != BuildConfig.APPLICATION_ID) {
            throw IOException("Pobrany APK nale\u017cy do innej aplikacji.")
        }
    }

    private const val UPDATE_DIRECTORY = "app-updates"
    private val SHA_256_REGEX = Regex("(?i)\\b[0-9a-f]{64}\\b")
}

object UpdatePackageInstaller {
    fun createInstallIntent(context: Context, apkFile: File): Intent {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun launch(context: Context, apkFile: File): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }

        context.startActivity(createInstallIntent(context, apkFile))
        return true
    }

    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
}
