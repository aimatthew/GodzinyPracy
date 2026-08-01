package pl.godzinypracy.workly.ui

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.identity.AuthorizationResult
import androidx.lifecycle.AndroidViewModel
import pl.godzinypracy.workly.data.WorkEntry
import pl.godzinypracy.workly.data.WorkRepository
import pl.godzinypracy.workly.data.WorkBackupCodec
import pl.godzinypracy.workly.sync.DriveAppDataClient
import pl.godzinypracy.workly.sync.GoogleDriveSyncScheduler
import pl.godzinypracy.workly.sync.GoogleDriveSyncState
import pl.godzinypracy.workly.sync.GoogleDriveSyncStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class WorkUiState(
    val entries: Map<LocalDate, WorkEntry> = emptyMap(),
    val visibleMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val dailyTargetMinutes: Int = 8 * 60,
    val hourlyRateCents: Int = 0
)

class WorkViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WorkRepository(application)
    private val applicationContext = application.applicationContext
    private val syncStore = GoogleDriveSyncStore(applicationContext)
    private val _googleDriveSyncState = MutableStateFlow(syncStore.load())
    val googleDriveSyncState: StateFlow<GoogleDriveSyncState> =
        _googleDriveSyncState.asStateFlow()
    private val syncPreferencesListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            _googleDriveSyncState.value = syncStore.load()
        }

    init {
        syncStore.registerListener(syncPreferencesListener)
        GoogleDriveSyncScheduler.schedulePeriodic(applicationContext)
    }

    private val _uiState = MutableStateFlow(
        WorkUiState(
            entries = repository.loadEntries().associateBy { it.date },
            dailyTargetMinutes = repository.loadDailyTargetMinutes(),
            hourlyRateCents = repository.loadHourlyRateCents()
        )
    )
    val uiState: StateFlow<WorkUiState> = _uiState.asStateFlow()

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date, visibleMonth = YearMonth.from(date)) }
    }

    fun showMonth(month: YearMonth) {
        _uiState.update { state -> state.copy(visibleMonth = month) }
    }

    fun saveEntry(entry: WorkEntry) {
        _uiState.update { state ->
            val updated = state.entries + (entry.date to entry)
            repository.saveEntries(updated.values)
            scheduleGoogleSync()
            state.copy(entries = updated, selectedDate = entry.date, visibleMonth = YearMonth.from(entry.date))
        }
    }

    fun deleteEntry(date: LocalDate) {
        _uiState.update { state ->
            val updated = state.entries - date
            repository.saveEntries(updated.values)
            scheduleGoogleSync()
            state.copy(entries = updated)
        }
    }

    fun setDailyTarget(minutes: Int) {
        repository.saveDailyTargetMinutes(minutes)
        scheduleGoogleSync()
        _uiState.update { it.copy(dailyTargetMinutes = minutes) }
    }

    fun setHourlyRate(cents: Int) {
        repository.saveHourlyRateCents(cents)
        scheduleGoogleSync()
        _uiState.update { it.copy(hourlyRateCents = cents) }
    }

    fun clearAllEntries() {
        repository.saveEntries(emptyList())
        scheduleGoogleSync()
        _uiState.update { it.copy(entries = emptyMap()) }
    }

    fun markGoogleConnecting() {
        syncStore.markConnecting()
    }

    fun markGoogleError(message: String) {
        syncStore.markError(message)
    }

    fun handleGoogleAuthorization(result: AuthorizationResult) {
        val token = result.accessToken
        if (token.isNullOrBlank()) {
            syncStore.markError("Google nie zwr\u00f3ci\u0142 tokenu dost\u0119pu.")
            return
        }

        val authorizationEmail = result.toGoogleSignInAccount()?.account?.name

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val client = DriveAppDataClient(token)
                    val accountInfo = client.getAccountInfo()
                    val accountEmail = authorizationEmail
                        ?: accountInfo.email
                        ?: error("Google Drive nie zwr\u00f3ci\u0142 adresu konta.")
                    Triple(accountEmail, accountInfo.photoUrl, client.findBackup())
                }
            }.onSuccess { (accountEmail, accountPhotoUrl, remote) ->
                syncStore.saveAccountProfile(accountEmail, accountPhotoUrl)
                if (remote == null) {
                    syncStore.enable(accountEmail)
                    GoogleDriveSyncScheduler.schedulePeriodic(applicationContext)
                    GoogleDriveSyncScheduler.enqueueNow(applicationContext)
                } else {
                    syncStore.markDecisionRequired(accountEmail, remote.modifiedEpochMillis)
                }
            }.onFailure {
                syncStore.markError("Nie uda\u0142o si\u0119 odczyta\u0107 konta lub kopii z Dysku Google.")
            }
        }
    }

    fun restoreGoogleBackup(accessToken: String) {
        val remote = syncStore.load()
        val accountEmail = remote.accountEmail ?: return
        syncStore.markSyncing()
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val client = DriveAppDataClient(accessToken)
                    val info = client.findBackup()
                        ?: error("Nie znaleziono kopii Google Drive.")
                    val raw = client.downloadBackup(info)
                    WorkBackupCodec.decodeInto(repository, raw)
                }
            }.onSuccess {
                reloadLocalData()
                syncStore.enable(accountEmail)
                syncStore.markSynced()
                GoogleDriveSyncScheduler.schedulePeriodic(applicationContext)
            }.onFailure {
                syncStore.markError("Nie uda\u0142o si\u0119 przywr\u00f3ci\u0107 kopii Google Drive.")
            }
        }
    }

    fun keepLocalDataAndEnableGoogle() {
        val accountEmail = syncStore.load().accountEmail ?: return
        syncStore.enable(accountEmail)
        GoogleDriveSyncScheduler.schedulePeriodic(applicationContext)
        GoogleDriveSyncScheduler.enqueueNow(applicationContext)
    }

    fun deleteCloudBackup(
        accessToken: String,
        onDeleted: (String) -> Unit
    ) {
        val accountEmail = syncStore.load().accountEmail ?: return
        syncStore.markSyncing()
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    DriveAppDataClient(accessToken).deleteBackup()
                }
            }.onSuccess {
                GoogleDriveSyncScheduler.cancel(applicationContext)
                syncStore.clear()
                onDeleted(accountEmail)
            }.onFailure {
                syncStore.markError("Nie uda\u0142o si\u0119 usun\u0105\u0107 kopii z Dysku Google.")
            }
        }
    }

    fun syncGoogleNow() {
        GoogleDriveSyncScheduler.enqueueNow(applicationContext)
    }

    private fun reloadLocalData() {
        _uiState.update {
            it.copy(
                entries = repository.loadEntries().associateBy { entry -> entry.date },
                dailyTargetMinutes = repository.loadDailyTargetMinutes(),
                hourlyRateCents = repository.loadHourlyRateCents()
            )
        }
    }

    private fun scheduleGoogleSync() {
        GoogleDriveSyncScheduler.enqueueNow(applicationContext)
    }

    override fun onCleared() {
        syncStore.unregisterListener(syncPreferencesListener)
        super.onCleared()
    }
}
