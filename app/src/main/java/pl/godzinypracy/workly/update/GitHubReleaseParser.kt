package pl.godzinypracy.workly.update

import org.json.JSONObject

internal fun parseGitHubRelease(json: JSONObject): GitHubRelease? {
    val assets = json.optJSONArray("assets") ?: return null
    var apkName: String? = null
    var apkDownloadUrl: String? = null
    var sha256DownloadUrl: String? = null

    repeat(assets.length()) { index ->
        val asset = assets.optJSONObject(index) ?: return@repeat
        val name = asset.optString("name")
        val downloadUrl = asset.optString("browser_download_url")
        when {
            name.endsWith(".apk", ignoreCase = true) -> {
                apkName = name
                apkDownloadUrl = downloadUrl
            }

            name.endsWith(".apk.sha256", ignoreCase = true) -> {
                sha256DownloadUrl = downloadUrl
            }
        }
    }

    val resolvedApkName = apkName?.takeIf { it.isNotBlank() } ?: return null
    val resolvedApkUrl = apkDownloadUrl?.takeIf { it.isNotBlank() } ?: return null
    val resolvedShaUrl = sha256DownloadUrl?.takeIf { it.isNotBlank() } ?: return null
    val tagName = json.optString("tag_name").takeIf { it.isNotBlank() } ?: return null

    return GitHubRelease(
        tagName = tagName,
        displayName = json.optString("name").takeIf { it.isNotBlank() } ?: tagName,
        pageUrl = json.optString("html_url"),
        apkName = resolvedApkName,
        apkDownloadUrl = resolvedApkUrl,
        sha256DownloadUrl = resolvedShaUrl
    )
}
