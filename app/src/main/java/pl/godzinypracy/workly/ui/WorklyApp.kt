package pl.godzinypracy.workly.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pl.godzinypracy.workly.data.WorkDayType
import pl.godzinypracy.workly.data.WorkEntry
import pl.godzinypracy.workly.data.calculateEarningsCents
import pl.godzinypracy.workly.data.formatHourlyRateInput
import pl.godzinypracy.workly.data.formatMoney
import pl.godzinypracy.workly.data.parseHourlyRateCents
import pl.godzinypracy.workly.data.formatClock
import pl.godzinypracy.workly.data.formatMinutes
import pl.godzinypracy.workly.ui.screens.CalendarScreenPro
import pl.godzinypracy.workly.ui.screens.EntriesScreen
import pl.godzinypracy.workly.ui.screens.SettingsScreen
import pl.godzinypracy.workly.sync.GoogleDriveAuthorizationController
import pl.godzinypracy.workly.sync.GoogleDriveSyncStatus
import pl.godzinypracy.workly.ui.screens.StatisticsScreen
import pl.godzinypracy.workly.update.AppUpdateScheduler
import pl.godzinypracy.workly.update.UpdatePreferences
import java.time.LocalDate

private enum class AppTab(val label: String) {
    CALENDAR("Kalendarz"),
    ENTRIES("Wpisy"),
    STATISTICS("Statystyki"),
    SETTINGS("Więcej")
}

private enum class GoogleDriveAction {
    CONNECT,
    RESTORE,
    DISCONNECT
}

private enum class TimePickerTarget {
    START,
    END
}

@Composable
fun WorklyApp(viewModel: WorkViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val googleDriveSyncState by viewModel.googleDriveSyncState.collectAsState()
    val context = LocalContext.current
    val updatePreferences = remember(context) { UpdatePreferences(context) }
    var automaticUpdatesEnabled by remember {
        mutableStateOf(updatePreferences.automaticChecksEnabled)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val enabled = granted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        updatePreferences.setAutomaticChecksEnabled(enabled)
        automaticUpdatesEnabled = enabled
        if (enabled) AppUpdateScheduler.enable(context) else AppUpdateScheduler.disable(context)
    }

    fun setAutomaticUpdatesEnabled(enabled: Boolean) {
        if (!enabled) {
            updatePreferences.setAutomaticChecksEnabled(false)
            automaticUpdatesEnabled = false
            AppUpdateScheduler.disable(context)
            return
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            updatePreferences.setAutomaticChecksEnabled(true)
            automaticUpdatesEnabled = true
            AppUpdateScheduler.enable(context)
        }
    }

    LaunchedEffect(Unit) {
        if (automaticUpdatesEnabled) AppUpdateScheduler.enable(context)
    }
    val googleAuthorizationController = remember(context) {
        GoogleDriveAuthorizationController(context)
    }
    val googleAuthorizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK) {
            googleAuthorizationController.handleResolution(activityResult.data)
        } else {
            viewModel.markGoogleError("Anulowano po\u0142\u0105czenie z kontem Google.")
        }
    }

    fun authorizeGoogle(action: GoogleDriveAction) {
        val wasConnected = googleDriveSyncState.connected
        viewModel.markGoogleConnecting()
        googleAuthorizationController.authorize(
            accountEmail = googleDriveSyncState.accountEmail,
            onResolutionRequired = { pendingIntent ->
                googleAuthorizationLauncher.launch(
                    IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                )
            },
            onAuthorized = { result ->
                val token = result.accessToken
                if (token.isNullOrBlank()) {
                    viewModel.markGoogleError("Google nie zwr\u00f3ci\u0142 tokenu dost\u0119pu.")
                } else {
                    when (action) {
                        GoogleDriveAction.CONNECT -> {
                            if (wasConnected) viewModel.keepLocalDataAndEnableGoogle()
                            else viewModel.handleGoogleAuthorization(result)
                        }
                        GoogleDriveAction.RESTORE -> viewModel.restoreGoogleBackup(token)
                        GoogleDriveAction.DISCONNECT -> viewModel.deleteCloudBackup(token) { email ->
                            googleAuthorizationController.revoke(
                                accountEmail = email,
                                onComplete = {},
                                onError = viewModel::markGoogleError
                            )
                        }
                    }
                }
            },
            onError = viewModel::markGoogleError
        )
    }

    var selectedTabName by rememberSaveable { mutableStateOf(AppTab.CALENDAR.name) }
    val selectedTab = AppTab.valueOf(selectedTabName)
    var editorDate by remember { mutableStateOf<LocalDate?>(null) }

    BackHandler {
        when {
            editorDate != null -> editorDate = null
            selectedTab != AppTab.CALENDAR -> selectedTabName = AppTab.CALENDAR.name
            else -> Unit
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp
            ) {
                AppTab.entries.forEach { tab ->
                    val icon = when (tab) {
                        AppTab.CALENDAR -> Icons.Outlined.CalendarMonth
                        AppTab.ENTRIES -> Icons.AutoMirrored.Outlined.ListAlt
                        AppTab.STATISTICS -> Icons.Outlined.BarChart
                        AppTab.SETTINGS -> Icons.Outlined.Settings
                    }
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTabName = tab.name },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { contentPadding ->
        when (selectedTab) {
            AppTab.CALENDAR -> CalendarScreenPro(
                modifier = Modifier.padding(contentPadding),
                state = uiState,
                accountPhotoUrl = googleDriveSyncState.accountPhotoUrl,
                onMonthChange = viewModel::showMonth,
                onDayClick = { date ->
                    viewModel.selectDate(date)
                    editorDate = date
                },
                onSettingsClick = { selectedTabName = AppTab.SETTINGS.name }
            )
            AppTab.ENTRIES -> EntriesScreen(
                modifier = Modifier.padding(contentPadding),
                entries = uiState.entries.values.sortedByDescending { it.date },
                hourlyRateCents = uiState.hourlyRateCents,
                onEntryClick = { editorDate = it.date }
            )
            AppTab.STATISTICS -> StatisticsScreen(
                modifier = Modifier.padding(contentPadding),
                entries = uiState.entries.values.toList(),
                dailyTargetMinutes = uiState.dailyTargetMinutes,
                hourlyRateCents = uiState.hourlyRateCents
            )
            AppTab.SETTINGS -> SettingsScreen(
                modifier = Modifier.padding(contentPadding),
                dailyTargetMinutes = uiState.dailyTargetMinutes,
                hourlyRateCents = uiState.hourlyRateCents,
                entryCount = uiState.entries.size,
                googleDriveSyncState = googleDriveSyncState,
                automaticUpdatesEnabled = automaticUpdatesEnabled,
                onAutomaticUpdatesChange = { setAutomaticUpdatesEnabled(it) },
                onTargetChange = viewModel::setDailyTarget,
                onHourlyRateChange = viewModel::setHourlyRate,
                onConnectGoogle = { authorizeGoogle(GoogleDriveAction.CONNECT) },
                onSyncGoogleNow = viewModel::syncGoogleNow,
                onRestoreGoogle = { authorizeGoogle(GoogleDriveAction.RESTORE) },
                onKeepLocalForGoogle = viewModel::keepLocalDataAndEnableGoogle,
                onDisconnectGoogle = { authorizeGoogle(GoogleDriveAction.DISCONNECT) },
                onClearAll = viewModel::clearAllEntries
            )
        }
    }

    editorDate?.let { date ->
        val previousWorkEntry = uiState.entries.values
            .asSequence()
            .filter { it.type == WorkDayType.WORK && it.date < date }
            .maxByOrNull { it.date }
        EntryEditorSheet(
            date = date,
            existingEntry = uiState.entries[date],
            suggestedStartMinutes = previousWorkEntry?.startMinutes ?: 8 * 60,
            suggestedEndMinutes = previousWorkEntry?.endMinutes ?: 16 * 60,
            frequentStartMinutes = mostFrequentWorkTimes(uiState.entries.values, WorkEntry::startMinutes),
            frequentEndMinutes = mostFrequentWorkTimes(uiState.entries.values, WorkEntry::endMinutes),
            defaultHourlyRateCents = uiState.hourlyRateCents,
            onDismiss = { editorDate = null },
            onSave = {
                viewModel.saveEntry(it)
                editorDate = null
            },
            onDelete = {
                viewModel.deleteEntry(date)
                editorDate = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryEditorSheet(
    date: LocalDate,
    existingEntry: WorkEntry?,
    suggestedStartMinutes: Int,
    suggestedEndMinutes: Int,
    frequentStartMinutes: List<Int>,
    frequentEndMinutes: List<Int>,
    defaultHourlyRateCents: Int,
    onDismiss: () -> Unit,
    onSave: (WorkEntry) -> Unit,
    onDelete: () -> Unit
) {
    var startMinutes by remember(date, existingEntry, suggestedStartMinutes) {
        mutableIntStateOf(existingEntry?.startMinutes ?: suggestedStartMinutes)
    }
    var endMinutes by remember(date, existingEntry, suggestedEndMinutes) {
        mutableIntStateOf(existingEntry?.endMinutes ?: suggestedEndMinutes)
    }
    var breakMinutes by remember(date, existingEntry) { mutableIntStateOf(existingEntry?.breakMinutes ?: 0) }
    var type by remember(date, existingEntry) { mutableStateOf(existingEntry?.type ?: WorkDayType.WORK) }
    var note by remember(date, existingEntry) { mutableStateOf(existingEntry?.note.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var timePickerTarget by remember { mutableStateOf<TimePickerTarget?>(null) }
    var showOptionalFields by remember(date, existingEntry) {
        mutableStateOf(existingEntry?.hourlyRateOverrideCents != null || !existingEntry?.note.isNullOrBlank())
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var customRateText by remember(date, existingEntry) {
        mutableStateOf(existingEntry?.hourlyRateOverrideCents?.let(::formatHourlyRateInput).orEmpty())
    }
    val parsedCustomRateCents = customRateText.takeIf { it.isNotBlank() }?.let(::parseHourlyRateCents)
    val effectiveHourlyRateCents = parsedCustomRateCents ?: defaultHourlyRateCents


    val draft = WorkEntry(
        date = date,
        startMinutes = startMinutes,
        endMinutes = endMinutes,
        breakMinutes = breakMinutes,
        type = type,
        hourlyRateOverrideCents = if (type == WorkDayType.WORK) parsedCustomRateCents else null,
        note = note.trim()
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = date.formatPolishDate(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (existingEntry == null) "Nowy wpis" else "Edytuj wpis",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "Zamknij")
                    }
                }

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                WorkDayType.entries.forEach { option ->
                    DayTypeButton(
                        selected = type == option,
                        onClick = { type = option },
                        label = when (option) {
                            WorkDayType.WORK -> "Praca"
                            WorkDayType.VACATION -> "Urlop"
                            WorkDayType.SICK_LEAVE -> "L4"
                            WorkDayType.DAY_OFF -> "Wolne"
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (type == WorkDayType.WORK) {
                Spacer(Modifier.height(18.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TimeButton(
                            modifier = Modifier.weight(1f),
                            label = "Od",
                            minutes = startMinutes,
                            onClick = { timePickerTarget = TimePickerTarget.START }
                        )
                        Icon(
                            Icons.Outlined.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TimeButton(
                            modifier = Modifier.weight(1f),
                            label = "Do",
                            minutes = endMinutes,
                            onClick = { timePickerTarget = TimePickerTarget.END }
                        )
                    }
                }

                if (
                    existingEntry == null &&
                    startMinutes == suggestedStartMinutes &&
                    endMinutes == suggestedEndMinutes
                ) {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Outlined.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Godziny ${formatClock(suggestedStartMinutes)}\u2013${formatClock(suggestedEndMinutes)} zapami\u0119tane z poprzedniego dnia",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (draft.endsNextDay) {
                    Text(
                        text = "Zmiana kończy się następnego dnia",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text("Przerwa", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0, 15, 30, 45, 60).forEach { value ->
                        BreakOptionButton(
                            selected = breakMinutes == value,
                            onClick = { breakMinutes = value },
                            label = if (value == 0) "Brak" else "$value",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                    Text("Łącznie", style = MaterialTheme.typography.titleMedium)
                    Text(
                        formatMinutes(draft.workedMinutes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Zarobek", style = MaterialTheme.typography.labelMedium)
                            Text(
                                if (effectiveHourlyRateCents > 0) {
                                    formatMoney(calculateEarningsCents(draft.workedMinutes, effectiveHourlyRateCents))
                                } else {
                                    "\u2014"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                }
                }
                OptionalFieldsToggle(
                    expanded = showOptionalFields,
                    onToggle = { showOptionalFields = !showOptionalFields }
                )
                if (showOptionalFields) {

                OutlinedTextField(
                    value = customRateText,
                    onValueChange = { value ->
                        val validFormat = value.isEmpty() ||
                            value.matches(Regex("""\d{1,6}([,.]\d{0,2})?"""))
                        if (validFormat) customRateText = value
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Stawka tylko dla tego dnia") },
                    placeholder = { Text("Bez zmiany") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = {
                        Text(
                            if (defaultHourlyRateCents > 0) {
                                "Puste pole: stawka ogólna ${formatHourlyRateInput(defaultHourlyRateCents)} zł/godz."
                            } else {
                                "Puste pole: brak stawki ogólnej"
                            }
                        )
                    }
                )

                if (effectiveHourlyRateCents > 0 || parsedCustomRateCents != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Wypłata za ten dzień", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            formatMoney(calculateEarningsCents(draft.workedMinutes, effectiveHourlyRateCents)),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                }
            }
            if (type != WorkDayType.WORK) {
                OptionalFieldsToggle(
                    expanded = showOptionalFields,
                    onToggle = { showOptionalFields = !showOptionalFields }
                )
            }
            if (showOptionalFields) {


            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notatka (opcjonalnie)") },
                minLines = 2,
                maxLines = 4
            )
            }


            if (existingEntry != null) {
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                    Text("Usuń wpis")
                }
            }
            Spacer(Modifier.height(12.dp))
        }
            Surface(
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text("Anuluj")
                    }
                    Button(
                        onClick = { onSave(draft) },
                        modifier = Modifier.weight(2f),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text(if (existingEntry == null) "Dodaj" else "Zapisz")
                    }
                }
            }
    }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Usunąć wpis?") },
            text = { Text("Godziny z tego dnia zostaną usunięte z kalendarza i podsumowań.") },
            confirmButton = {
                TextButton(onClick = onDelete) { Text("Usuń") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Anuluj") }
            }
        )
    }

    timePickerTarget?.let { target ->
        val initialMinutes = when (target) {
            TimePickerTarget.START -> startMinutes
            TimePickerTarget.END -> endMinutes
        }
        WorklyTimePickerDialog(
            title = when (target) {
                TimePickerTarget.START -> "Godzina rozpocz\u0119cia"
                TimePickerTarget.END -> "Godzina zako\u0144czenia"
            },
            initialMinutes = initialMinutes,
            frequentMinutes = when (target) {
                TimePickerTarget.START -> frequentStartMinutes
                TimePickerTarget.END -> frequentEndMinutes
            },
            onDismiss = { timePickerTarget = null },
            onConfirm = { selectedMinutes ->
                when (target) {
                    TimePickerTarget.START -> startMinutes = selectedMinutes
                    TimePickerTarget.END -> endMinutes = selectedMinutes
                }
                timePickerTarget = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorklyTimePickerDialog(
    title: String,
    initialMinutes: Int,
    frequentMinutes: List<Int>,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = timePickerState)
                if (frequentMinutes.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Najcz\u0119\u015bciej u\u017cywane",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        frequentMinutes.forEach { minutes ->
                            FrequentTimeButton(
                                selected = timePickerState.hour == minutes / 60 &&
                                    timePickerState.minute == minutes % 60,
                                label = formatClock(minutes),
                                onClick = {
                                    timePickerState.hour = minutes / 60
                                    timePickerState.minute = minutes % 60
                                },
                                modifier = Modifier.padding(horizontal = 3.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(timePickerState.hour * 60 + timePickerState.minute)
                }
            ) {
                Text("Ustaw")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Anuluj")
            }
        }
    )
}

private fun mostFrequentWorkTimes(
    entries: Collection<WorkEntry>,
    timeSelector: (WorkEntry) -> Int
): List<Int> = entries
    .filter { it.type == WorkDayType.WORK }
    .groupBy(timeSelector)
    .entries
    .sortedWith(
        compareByDescending<Map.Entry<Int, List<WorkEntry>>> { it.value.size }
            .thenByDescending { group -> group.value.maxOf { it.date } }
    )
    .take(3)
    .map { it.key }

@Composable
private fun FrequentTimeButton(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
        ) {
            Text(label, maxLines = 1)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
        ) {
            Text(label, maxLines = 1)
        }
    }
}

@Composable
private fun TimeButton(
    modifier: Modifier,
    label: String,
    minutes: Int,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                formatClock(minutes),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DayTypeButton(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
        ) {
            Text(label, maxLines = 1)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
        ) {
            Text(label, maxLines = 1)
        }
    }
}

@Composable
private fun BreakOptionButton(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
        ) {
            Text(label, maxLines = 1)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
        ) {
            Text(label, maxLines = 1)
        }
    }
}

@Composable
private fun OptionalFieldsToggle(
    expanded: Boolean,
    onToggle: () -> Unit
) {
    TextButton(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 10.dp)
    ) {
        Icon(
            Icons.Outlined.Tune,
            contentDescription = null
        )
        Text(
            text = "Stawka i notatka",
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        Text(
            text = if (expanded) "Zwi\u0144" else "Rozwi\u0144",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null
        )
    }
}
