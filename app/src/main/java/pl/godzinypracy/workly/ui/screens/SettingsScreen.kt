package pl.godzinypracy.workly.ui.screens

import androidx.compose.ui.ExperimentalComposeUiApi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pl.godzinypracy.workly.BuildConfig
import pl.godzinypracy.workly.data.formatHourlyRateInput
import pl.godzinypracy.workly.data.formatMinutes
import pl.godzinypracy.workly.data.parseHourlyRateCents
import pl.godzinypracy.workly.sync.GoogleDriveSyncState
import kotlinx.coroutines.launch
import pl.godzinypracy.workly.update.AppUpdateChecker
import pl.godzinypracy.workly.update.ManualUpdateCheckResult
import pl.godzinypracy.workly.update.GitHubRelease
import pl.godzinypracy.workly.update.UpdatePackageDownloader
import pl.godzinypracy.workly.update.UpdatePackageInstaller


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    dailyTargetMinutes: Int,
    hourlyRateCents: Int,
    entryCount: Int,
    googleDriveSyncState: GoogleDriveSyncState,
    automaticUpdatesEnabled: Boolean,
    onAutomaticUpdatesChange: (Boolean) -> Unit,
    onTargetChange: (Int) -> Unit,
    onHourlyRateChange: (Int) -> Unit,
    onConnectGoogle: () -> Unit,
    onSyncGoogleNow: () -> Unit,
    onRestoreGoogle: () -> Unit,
    onKeepLocalForGoogle: () -> Unit,
    onDisconnectGoogle: () -> Unit,
    onClearAll: () -> Unit
) {
    var showClearConfirmation by remember { mutableStateOf(false) }
    var hourlyRateText by remember(hourlyRateCents) {
        mutableStateOf(formatHourlyRateInput(hourlyRateCents))
    }
    var hourlyRateError by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val updateCheckScope = rememberCoroutineScope()
    val context = LocalContext.current
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var manualUpdateMessage by remember { mutableStateOf<String?>(null) }
    var manualUpdateResult by remember { mutableStateOf<ManualUpdateCheckResult?>(null) }
    var isCheckingForUpdate by remember { mutableStateOf(false) }

    fun checkUpdatesNow() {
        if (isCheckingForUpdate) return
        isCheckingForUpdate = true
        manualUpdateResult = null
        manualUpdateMessage = null
        updateCheckScope.launch {
            manualUpdateResult = AppUpdateChecker.checkNow()
            isCheckingForUpdate = false
        }
    }

    fun downloadAndInstallUpdate(release: GitHubRelease) {
        if (isDownloadingUpdate) return
        isDownloadingUpdate = true
        manualUpdateMessage = "Pobieranie i sprawdzanie APK..."
        updateCheckScope.launch {
            try {
                val apkFile = UpdatePackageDownloader.download(context, release)
                val installerOpened = UpdatePackageInstaller.launch(context, apkFile)
                manualUpdateMessage = if (installerOpened) {
                    "Instalator Androida zosta\u0142 uruchomiony."
                } else {
                    "Zezw\u00f3l aplikacji na instalowanie aktualizacji, wr\u00f3\u0107 tutaj i naci\u015bnij ponownie."
                }
            } catch (_: Exception) {
                manualUpdateMessage =
                    "Nie uda\u0142o si\u0119 pobra\u0107 lub zweryfikowa\u0107 aktualizacji."
            } finally {
                isDownloadingUpdate = false
            }
        }

    }

    fun saveHourlyRate() {
        val parsedRate = parseHourlyRateCents(hourlyRateText)
        if (parsedRate == null) {
            hourlyRateError = true
        } else {
            onHourlyRateChange(parsedRate)
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }


    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text("USTAWIENIA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text("Więcej", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            "Dostosuj sposób liczenia czasu pracy",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(18.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Outlined.Payments, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Stawka godzinowa", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Na jej podstawie aplikacja wylicza szacowaną wypłatę za przepracowany czas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = hourlyRateText,
                    onValueChange = { value ->
                        if (value.length <= 10 && value.all { it.isDigit() || it == ',' || it == '.' }) {
                            hourlyRateText = value
                            hourlyRateError = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Stawka (zł / godz.)") },
                    singleLine = true,
                    isError = hourlyRateError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { saveHourlyRate() }),
                    supportingText = if (hourlyRateError) {
                        { Text("Wpisz poprawną kwotę, np. 32,50") }
                    } else null
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { saveHourlyRate() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Zapisz stawkę")
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(18.dp)) {
                Text("Dzienna norma", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Służy do wyliczania bilansu i nadgodzin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { onTargetChange((dailyTargetMinutes - 30).coerceAtLeast(60)) }) {
                        Icon(Icons.Outlined.Remove, contentDescription = "Zmniejsz normę o 30 minut")
                    }
                    Text(formatMinutes(dailyTargetMinutes), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { onTargetChange((dailyTargetMinutes + 30).coerceAtMost(24 * 60)) }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Zwiększ normę o 30 minut")
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        if (!googleDriveSyncState.connected) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Smartphone, contentDescription = null)
                    Text("Dane zapisane lokalnie", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "$entryCount wpisów znajduje się wyłącznie w pamięci aplikacji na tym telefonie.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Lock, contentDescription = null)
                    Text("Aplikacja nie wymaga konta ani internetu.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        }

        Spacer(Modifier.height(14.dp))
        GoogleDriveBackupCard(
            state = googleDriveSyncState,
            onConnect = onConnectGoogle,
            onSyncNow = onSyncGoogleNow,
            onRestore = onRestoreGoogle,
            onKeepLocal = onKeepLocalForGoogle,
            onDisconnect = onDisconnectGoogle
        )

        Spacer(Modifier.height(14.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Outlined.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(Modifier.weight(1f)) {
                    Text("Aktualizacje aplikacji", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (BuildConfig.GITHUB_REPOSITORY.isBlank()) {
                            "Funkcja zostanie aktywowana po połączeniu projektu z GitHub."
                        } else {
                            "Sprawdzaj nowe wydania na GitHub co 12 godzin i pokaż powiadomienie."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = automaticUpdatesEnabled,
                    onCheckedChange = onAutomaticUpdatesChange,
                    enabled = BuildConfig.GITHUB_REPOSITORY.isNotBlank()
                )
            }
                Spacer(Modifier.height(14.dp))
                OutlinedButton(
                    onClick = { checkUpdatesNow() },
                    enabled = BuildConfig.GITHUB_REPOSITORY.isNotBlank() && !isCheckingForUpdate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (isCheckingForUpdate) {
                            "Sprawdzanie..."
                        } else {
                            "Sprawd\u017a aktualizacje teraz"
                        }
                    )
                }

                manualUpdateResult?.let { result ->
                    Spacer(Modifier.height(10.dp))
                    when (result) {
                        is ManualUpdateCheckResult.UpdateAvailable -> {
                            Column {
                                Text(
                                    "Dost\u0119pna aktualizacja: ${result.release.displayName}.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Button(
                                    onClick = { downloadAndInstallUpdate(result.release) },
                                    enabled = !isDownloadingUpdate,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        if (isDownloadingUpdate) {
                                            "Pobieranie APK..."
                                        } else {
                                            "Pobierz i zainstaluj"
                                        }
                                    )
                                }
                                manualUpdateMessage?.let { message ->
                                    Spacer(Modifier.height(6.dp))
                                    Text(message, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        ManualUpdateCheckResult.UpToDate -> Text(
                            "Masz najnowsz\u0105 wersj\u0119 (${BuildConfig.VERSION_NAME}).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        ManualUpdateCheckResult.NoPublishedRelease -> Text(
                            "Na GitHub nie ma jeszcze opublikowanego wydania.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        ManualUpdateCheckResult.RepositoryNotConfigured -> Text(
                            "Repozytorium aktualizacji nie jest skonfigurowane.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )

                        ManualUpdateCheckResult.Failed -> Text(
                            "Nie uda\u0142o si\u0119 sprawdzi\u0107 aktualizacji. Sprawd\u017a po\u0142\u0105czenie z internetem.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Zarządzanie danymi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { showClearConfirmation = true },
            enabled = entryCount > 0,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
            Text("Usuń wszystkie wpisy")
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "Godziny Pracy • wersja ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(20.dp))
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Usunąć wszystkie dane?") },
            text = { Text("Ta operacja usunie wszystkie zapisane dni pracy z tego telefonu.") },
            confirmButton = {
                Button(onClick = {
                    onClearAll()
                    showClearConfirmation = false
                }) { Text("Usuń wszystko") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) { Text("Anuluj") }
            }
        )
    }
}
