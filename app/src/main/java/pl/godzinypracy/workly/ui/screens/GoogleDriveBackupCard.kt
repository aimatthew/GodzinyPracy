package pl.godzinypracy.workly.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.godzinypracy.workly.sync.GoogleDriveSyncState
import pl.godzinypracy.workly.sync.GoogleDriveSyncStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun GoogleDriveBackupCard(
    state: GoogleDriveSyncState,
    onConnect: () -> Unit,
    onSyncNow: () -> Unit,
    onRestore: () -> Unit,
    onKeepLocal: () -> Unit,
    onDisconnect: () -> Unit
) {
    var showDisconnectConfirmation by remember { mutableStateOf(false) }
    val isBusy = state.status == GoogleDriveSyncStatus.CONNECTING ||
        state.status == GoogleDriveSyncStatus.SYNCING

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = when {
                        state.status == GoogleDriveSyncStatus.SYNCED -> Icons.Outlined.CloudDone
                        state.connected -> Icons.Outlined.CloudSync
                        else -> Icons.Outlined.CloudOff
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        "Kopia Google Drive",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    state.accountEmail?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = syncDescription(state),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.lastSyncEpochMillis?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Ostatnia kopia: ${formatSyncDate(it)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            state.message?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (isBusy) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(14.dp))
            if (!state.connected || state.status == GoogleDriveSyncStatus.NEEDS_RECONNECT) {
                Button(
                    onClick = onConnect,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Połącz z Google")
                }
            } else {
                Button(
                    onClick = onSyncNow,
                    enabled = state.enabled && !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Synchronizuj teraz")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onRestore,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Restore, contentDescription = null)
                    Text("Przywróć kopię")
                }
                TextButton(
                    onClick = { showDisconnectConfirmation = true },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.DeleteForever, contentDescription = null)
                    Text("Usuń kopię i odłącz")
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "Aplikacja ma dostęp tylko do własnego, ukrytego pliku kopii. Nie widzi pozostałych plików na Dysku.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (state.status == GoogleDriveSyncStatus.DECISION_REQUIRED) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Znaleziono istniejącą kopię") },
            text = {
                Text(
                    "Wybierz dane, które mają zostać użyte. Przywrócenie zastąpi dane zapisane obecnie na telefonie."
                )
            },
            confirmButton = {
                Button(onClick = onRestore) { Text("Przywróć kopię") }
            },
            dismissButton = {
                TextButton(onClick = onKeepLocal) { Text("Zachowaj dane telefonu") }
            }
        )
    }

    if (showDisconnectConfirmation) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirmation = false },
            title = { Text("Usunąć kopię Google?") },
            text = {
                Text(
                    "Kopia na Dysku zostanie trwale usunięta, a konto odłączone. Dane zapisane na telefonie pozostaną bez zmian."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showDisconnectConfirmation = false
                    onDisconnect()
                }) { Text("Usuń i odłącz") }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirmation = false }) {
                    Text("Anuluj")
                }
            }
        )
    }
}

private fun syncDescription(state: GoogleDriveSyncState): String = when (state.status) {
    GoogleDriveSyncStatus.LOCAL_ONLY ->
        "Połącz konto, aby godziny i stawki zapisywały się automatycznie na Twoim Dysku Google."
    GoogleDriveSyncStatus.CONNECTING -> "Łączenie z kontem Google…"
    GoogleDriveSyncStatus.DECISION_REQUIRED -> "Kopia czeka na wybór sposobu przywrócenia."
    GoogleDriveSyncStatus.SYNCING -> "Trwa zabezpieczanie najnowszych zmian…"
    GoogleDriveSyncStatus.SYNCED -> "Synchronizacja automatyczna jest aktywna."
    GoogleDriveSyncStatus.NEEDS_RECONNECT -> "Połączenie z Google wymaga ponownego potwierdzenia."
    GoogleDriveSyncStatus.ERROR -> "Ostatnia próba synchronizacji nie powiodła się."
}

private fun formatSyncDate(epochMillis: Long): String = runCatching {
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale("pl", "PL")))
}.getOrDefault("—")
