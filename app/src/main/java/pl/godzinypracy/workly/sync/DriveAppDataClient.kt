package pl.godzinypracy.workly.sync

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

data class DriveBackupInfo(
    val fileId: String,
    val modifiedEpochMillis: Long?
)
data class DriveAccountInfo(
    val email: String?,
    val photoUrl: String?
)


class DriveHttpException(
    val statusCode: Int,
    message: String
) : IOException(message)

class DriveAppDataClient(private val accessToken: String) {

    fun getAccountInfo(): DriveAccountInfo {
        val response = request(
            method = "GET",
            url = "$DRIVE_ABOUT_URL?fields=user(emailAddress,photoLink)"
        )
        val user = JSONObject(response).optJSONObject("user")
        return DriveAccountInfo(
            email = user?.optString("emailAddress")?.takeIf { it.isNotBlank() },
            photoUrl = user?.optString("photoLink")?.takeIf { it.isNotBlank() }
        )
    }

    fun findBackup(): DriveBackupInfo? {
        val query = "name = '$BACKUP_FILE_NAME' and trashed = false"
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val response = request(
            method = "GET",
            url = "$DRIVE_FILES_URL?spaces=appDataFolder&q=$encodedQuery" +
                "&fields=files(id,modifiedTime)&pageSize=10"
        )
        val files = JSONObject(response).optJSONArray("files") ?: JSONArray()
        return buildList {
            repeat(files.length()) { index ->
                val file = files.getJSONObject(index)
                add(
                    DriveBackupInfo(
                        fileId = file.getString("id"),
                        modifiedEpochMillis = file.optString("modifiedTime")
                            .takeIf { it.isNotBlank() }
                            ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                    )
                )
            }
        }.maxByOrNull { it.modifiedEpochMillis ?: 0L }
    }

    fun downloadBackup(info: DriveBackupInfo): String = request(
        method = "GET",
        url = "$DRIVE_FILES_URL/${info.fileId}?alt=media"
    )

    fun uploadBackup(contents: String): DriveBackupInfo {
        val existing = findBackup()
        val response = if (existing == null) {
            createBackup(contents)
        } else {
            updateBackup(existing.fileId, contents)
        }
        val json = JSONObject(response)
        return DriveBackupInfo(
            fileId = json.getString("id"),
            modifiedEpochMillis = json.optString("modifiedTime")
                .takeIf { it.isNotBlank() }
                ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
        )
    }

    fun deleteBackup() {
        findBackup()?.let { info ->
            request(
                method = "DELETE",
                url = "$DRIVE_FILES_URL/${info.fileId}"
            )
        }
    }

    private fun createBackup(contents: String): String {
        val boundary = "workly-${UUID.randomUUID()}"
        val metadata = JSONObject()
            .put("name", BACKUP_FILE_NAME)
            .put("mimeType", MIME_TYPE)
            .put("parents", JSONArray().put("appDataFolder"))
        val body = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata.toString())
            append("\r\n--$boundary\r\n")
            append("Content-Type: $MIME_TYPE; charset=UTF-8\r\n\r\n")
            append(contents)
            append("\r\n--$boundary--\r\n")
        }.toByteArray(StandardCharsets.UTF_8)
        return request(
            method = "POST",
            url = "$DRIVE_UPLOAD_URL?uploadType=multipart&fields=id,modifiedTime",
            contentType = "multipart/related; boundary=$boundary",
            body = body
        )
    }

    private fun updateBackup(fileId: String, contents: String): String = request(
        method = "PATCH",
        url = "$DRIVE_UPLOAD_URL/$fileId?uploadType=media&fields=id,modifiedTime",
        contentType = "$MIME_TYPE; charset=UTF-8",
        body = contents.toByteArray(StandardCharsets.UTF_8)
    )

    private fun request(
        method: String,
        url: String,
        contentType: String? = null,
        body: ByteArray? = null
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            if (method == "PATCH") {
                requestMethod = "POST"
                setRequestProperty("X-HTTP-Method-Override", "PATCH")
            } else {
                requestMethod = method
            }
            connectTimeout = 15_000
            readTimeout = 30_000
            useCaches = false
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            if (contentType != null) setRequestProperty("Content-Type", contentType)
            if (body != null) {
                doOutput = true
                setFixedLengthStreamingMode(body.size)
            }
        }

        return try {
            if (body != null) connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(StandardCharsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            if (status !in 200..299) {
                throw DriveHttpException(
                    statusCode = status,
                    message = "Google Drive zwrócił błąd $status: ${response.take(300)}"
                )
            }
            response
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        const val BACKUP_FILE_NAME = "workly-backup-v1.json"
        private const val MIME_TYPE = "application/json"
        private const val DRIVE_ABOUT_URL = "https://www.googleapis.com/drive/v3/about"
        private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
    }
}
