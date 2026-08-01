package pl.godzinypracy.workly.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.godzinypracy.workly.data.WorkDayType
import pl.godzinypracy.workly.data.WorkEntry
import pl.godzinypracy.workly.data.calculateEntryEarningsCents
import pl.godzinypracy.workly.data.effectiveHourlyRateCents
import pl.godzinypracy.workly.data.formatClock
import pl.godzinypracy.workly.data.formatHourlyRateInput
import pl.godzinypracy.workly.data.formatMoney
import pl.godzinypracy.workly.data.formatMinutes
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun EntriesScreen(
    modifier: Modifier = Modifier,
    entries: List<WorkEntry>,
    hourlyRateCents: Int,
    onEntryClick: (WorkEntry) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text("HISTORIA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text("Wszystkie wpisy", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            "${entries.size} zapisanych dni na tym telefonie",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(18.dp))

        if (entries.isEmpty()) {
            EmptyEntries(Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(entries, key = { it.date.toEpochDay() }) { entry ->
                    EntryRow(
                        entry = entry,
                        hourlyRateCents = hourlyRateCents,
                        onClick = { onEntryClick(entry) }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: WorkEntry,
    hourlyRateCents: Int,
    onClick: () -> Unit
) {
    val locale = Locale.forLanguageTag("pl-PL")
    val effectiveRateCents = entry.effectiveHourlyRateCents(hourlyRateCents)
    val day = entry.date.format(DateTimeFormatter.ofPattern("dd", locale))
    val month = entry.date.format(DateTimeFormatter.ofPattern("MMM", locale)).removeSuffix(".").uppercase(locale)
    val weekday = entry.date.format(DateTimeFormatter.ofPattern("EEEE", locale)).replaceFirstChar { it.titlecase(locale) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(day, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(month, style = MaterialTheme.typography.labelSmall)
                }
            }

            Column(Modifier.weight(1f)) {
                Text(weekday, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val detail = when (entry.type) {
                    WorkDayType.WORK -> "${formatClock(entry.startMinutes)}–${formatClock(entry.endMinutes)}" +
                        if (entry.breakMinutes > 0) " • przerwa ${entry.breakMinutes} min" else ""
                    else -> entry.type.label
                }
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (entry.note.isNotBlank()) {
                    Text(entry.note, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                if (entry.type == WorkDayType.WORK) {
                    Text(
                        formatMinutes(entry.workedMinutes, compact = true),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (effectiveRateCents > 0 || entry.hourlyRateOverrideCents != null) {
                        Text(
                            formatMoney(calculateEntryEarningsCents(entry, hourlyRateCents)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (entry.hourlyRateOverrideCents != null) {
                            Text(
                                "${if (effectiveRateCents == 0) "0" else formatHourlyRateInput(effectiveRateCents)} zł/godz.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = "Edytuj wpis")
            }
        }
    }
}

@Composable
private fun EmptyEntries(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text("Brak wpisów", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Wybierz dzień w kalendarzu i dodaj godziny pracy.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}
