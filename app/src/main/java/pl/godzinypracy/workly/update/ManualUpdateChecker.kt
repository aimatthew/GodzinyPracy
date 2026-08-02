package pl.godzinypracy.workly.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pl.godzinypracy.workly.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface ManualUpdateCheckResult {
    data class UpdateAvailable(val release: GitHubRelease) : ManualUpdateCheckResult
    data object UpToDate : ManualUpdateCheckResult
    data object NoPublishedRelease : ManualUpdateCheckResult
    data object RepositoryNotConfigured : ManualUpdateCheckResult
    data object Failed : ManualUpdateCheckResult
}

object AppUpdateChecker {
    suspend fun checkNow(): ManualUpdateCheckResult = withContext(Dispatchers.IO) {
        val repository = BuildConfig.GITHUB_REPOSITORY.trim()
        if (repository.isBlank()) {
            return@withContext ManualUpdateCheckResult.RepositoryNotConfigured
        }

        try {
            val release = fetchLatestRelease(repository)
                ?: return@withContext ManualUpdateCheckResult.NoPublishedRelease
            if (isNewerVersion(release.tagName, BuildConfig.VERSION_NAME)) {
                ManualUpdateCheckResult.UpdateAvailable(release)
            } else {
                ManualUpdateCheckResult.UpToDate
            }
        } catch (_: Exception) {
            ManualUpdateCheckResult.Failed
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
                    val tagName = json.getString("tag_name")
                    GitHubRelease(
                        tagName = tagName,
                        displayName = json.optString("name").takeIf { it.isNotBlank() } ?: tagName,
                        pageUrl = json.getString("html_url")
                    )
                }

                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw IOException("GitHub Releases HTTP $responseCode")
            }
        } finally {
            connection.disconnect()
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
}
