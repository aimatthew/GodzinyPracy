package pl.godzinypracy.workly.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
        EntryEditorSheet(
            date = date,
            existingEntry = uiState.entries[date],
            suggestedStartMinutes = uiState.entries.values
                .asSequence()
                .filter { it.type == WorkDayType.WORK && it.date < date }
                .maxByOrNull { it.date }
                ?.startMinutes ?: 8 * 60,
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
    defaultHourlyRateCents: Int,
    onDismiss: () -> Unit,
    onSave: (WorkEntry) -> Unit,
    onDelete: () -> Unit
) {
    var startMinutes by remember(date, existingEntry, suggestedStartMinutes) {
        mutableIntStateOf(existingEntry?.startMinutes ?: suggestedStartMinutes)
    }
    var endMinutes by remember(date, existingEntry) { mutableIntStateOf(existingEntry?.endMinutes ?: 16 * 60) }
    var breakMinutes by remember(date, existingEntry) { mutableIntStateOf(existingEntry?.breakMinutes ?: 0) }
    var type by remember(date, existingEntry) { mutableStateOf(existingEntry?.type ?: WorkDayType.WORK) }
    var note by remember(date, existingEntry) { mutableStateOf(existingEntry?.note.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
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

    fun showTimePicker(currentMinutes: Int, onSelected: (Int) -> Unit) {
        TimePickerDialog(
            context,
            { _, hour, minute -> onSelected(hour * 60 + minute) },
            currentMinutes / 60,
            currentMinutes % 60,
            true
        ).show()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = date.formatPolishDate(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (existingEntry == null) "Dodaj wpis do kalendarza" else "Edytuj zapisane godziny",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkDayType.entries.chunked(2).forEach { options ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        options.forEach { option ->
                            FilterChip(
                                selected = type == option,
                                onClick = { type = option },
                                modifier = Modifier.weight(1f),
                                label = {
                                    Text(
                                        text = option.label,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        maxLines = 1
                                    )
                                }
                            )
                        }
                    }
                }

            }
            if (type == WorkDayType.WORK) {
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TimeButton(
                        modifier = Modifier.weight(1f),
                        label = "Od",
                        minutes = startMinutes,
                        onClick = { showTimePicker(startMinutes) { startMinutes = it } }
                    )
                    TimeButton(
                        modifier = Modifier.weight(1f),
                        label = "Do",
                        minutes = endMinutes,
                        onClick = { showTimePicker(endMinutes) { endMinutes = it } }
                    )
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(listOf(0, 15, 30), listOf(45, 60)).forEach { rowValues ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowValues.forEach { value ->
                                AssistChip(
                                    onClick = { breakMinutes = value },
                                    label = { Text(if (value == 0) "Brak" else "$value min") },
                                    leadingIcon = if (breakMinutes == value) {
                                        { Icon(Icons.Outlined.Schedule, contentDescription = null) }
                                    } else null
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Łącznie", style = MaterialTheme.typography.titleMedium)
                    Text(
                        formatMinutes(draft.workedMinutes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

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

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notatka (opcjonalnie)") },
                minLines = 2,
                maxLines = 4
            )

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { onSave(draft) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (existingEntry == null) "Dodaj do kalendarza" else "Zapisz zmiany")
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
}

@Composable
private fun TimeButton(
    modifier: Modifier,
    label: String,
    minutes: Int,
    onClick: () -> Unit
) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
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
