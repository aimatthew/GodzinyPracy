package pl.godzinypracy.workly.sync

enum class GoogleDriveSyncStatus {
    LOCAL_ONLY,
    CONNECTING,
    DECISION_REQUIRED,
    SYNCING,
    SYNCED,
    NEEDS_RECONNECT,
    ERROR
}

data class GoogleDriveSyncState(
    val accountEmail: String? = null,
    val accountPhotoUrl: String? = null,
    val enabled: Boolean = false,
    val status: GoogleDriveSyncStatus = GoogleDriveSyncStatus.LOCAL_ONLY,
    val lastSyncEpochMillis: Long? = null,
    val remoteModifiedEpochMillis: Long? = null,
    val message: String? = null
) {
    val connected: Boolean get() = accountEmail != null
}
