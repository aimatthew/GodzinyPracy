package pl.godzinypracy.workly.sync

import android.content.Context
import android.content.SharedPreferences

class GoogleDriveSyncStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun load(): GoogleDriveSyncState = GoogleDriveSyncState(
        accountEmail = preferences.getString(KEY_ACCOUNT_EMAIL, null),
        accountPhotoUrl = preferences.getString(KEY_ACCOUNT_PHOTO_URL, null),
        enabled = preferences.getBoolean(KEY_ENABLED, false),
        status = runCatching {
            GoogleDriveSyncStatus.valueOf(
                preferences.getString(KEY_STATUS, GoogleDriveSyncStatus.LOCAL_ONLY.name)
                    ?: GoogleDriveSyncStatus.LOCAL_ONLY.name
            )
        }.getOrDefault(GoogleDriveSyncStatus.LOCAL_ONLY),
        lastSyncEpochMillis = preferences.optionalLong(KEY_LAST_SYNC),
        remoteModifiedEpochMillis = preferences.optionalLong(KEY_REMOTE_MODIFIED),
        message = preferences.getString(KEY_MESSAGE, null)
    )

    fun markConnecting() {
        preferences.edit()
            .putString(KEY_STATUS, GoogleDriveSyncStatus.CONNECTING.name)
            .remove(KEY_MESSAGE)
            .apply()
    }

    fun saveAccountProfile(email: String, photoUrl: String?) {
        preferences.edit()
            .putString(KEY_ACCOUNT_EMAIL, email)
            .putOptionalString(KEY_ACCOUNT_PHOTO_URL, photoUrl)
            .apply()
    }

    fun markDecisionRequired(email: String, remoteModifiedEpochMillis: Long?) {
        preferences.edit()
            .putString(KEY_ACCOUNT_EMAIL, email)
            .putBoolean(KEY_ENABLED, false)
            .putString(KEY_STATUS, GoogleDriveSyncStatus.DECISION_REQUIRED.name)
            .putOptionalLong(KEY_REMOTE_MODIFIED, remoteModifiedEpochMillis)
            .remove(KEY_MESSAGE)
            .apply()
    }

    fun enable(email: String) {
        preferences.edit()
            .putString(KEY_ACCOUNT_EMAIL, email)
            .putBoolean(KEY_ENABLED, true)
            .putString(KEY_STATUS, GoogleDriveSyncStatus.SYNCING.name)
            .remove(KEY_MESSAGE)
            .apply()
    }

    fun markSyncing() {
        preferences.edit()
            .putString(KEY_STATUS, GoogleDriveSyncStatus.SYNCING.name)
            .remove(KEY_MESSAGE)
            .apply()
    }

    fun markSynced(atEpochMillis: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putBoolean(KEY_ENABLED, true)
            .putString(KEY_STATUS, GoogleDriveSyncStatus.SYNCED.name)
            .putLong(KEY_LAST_SYNC, atEpochMillis)
            .remove(KEY_REMOTE_MODIFIED)
            .remove(KEY_MESSAGE)
            .apply()
    }

    fun markNeedsReconnect(message: String) {
        preferences.edit()
            .putString(KEY_STATUS, GoogleDriveSyncStatus.NEEDS_RECONNECT.name)
            .putString(KEY_MESSAGE, message)
            .apply()
    }

    fun markError(message: String) {
        preferences.edit()
            .putString(KEY_STATUS, GoogleDriveSyncStatus.ERROR.name)
            .putString(KEY_MESSAGE, message)
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun SharedPreferences.optionalLong(key: String): Long? =
        if (contains(key)) getLong(key, 0L) else null

    private fun SharedPreferences.Editor.putOptionalLong(
        key: String,
        value: Long?
    ): SharedPreferences.Editor = if (value == null) remove(key) else putLong(key, value)

    private fun SharedPreferences.Editor.putOptionalString(
        key: String,
        value: String?
    ): SharedPreferences.Editor = if (value == null) remove(key) else putString(key, value)

    companion object {
        private const val PREFERENCES_NAME = "workly_google_drive_sync"
        private const val KEY_ACCOUNT_EMAIL = "account_email"
        private const val KEY_ACCOUNT_PHOTO_URL = "account_photo_url"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_STATUS = "status"
        private const val KEY_LAST_SYNC = "last_sync_epoch_millis"
        private const val KEY_REMOTE_MODIFIED = "remote_modified_epoch_millis"
        private const val KEY_MESSAGE = "message"
    }
}
