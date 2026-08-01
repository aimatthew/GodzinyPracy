package pl.godzinypracy.workly.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.godzinypracy.workly.data.WorkDayType
import pl.godzinypracy.workly.data.WorkEntry
import pl.godzinypracy.workly.data.calculateEntriesEarningsCents
import pl.godzinypracy.workly.data.formatMoney
import pl.godzinypracy.workly.data.formatMinutes
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class StatisticsRange(val label: String) {
    MONTH("Miesiąc"), YEAR("Rok"), YEARS("Lata")
}

private data class ChartPoint(val label: String, val minutes: Int)

@Composable
fun StatisticsScreen(
    modifier: Modifier = Modifier,
    entries: List<WorkEntry>,
    dailyTargetMinutes: Int,
    hourlyRateCents: Int
) {
    var range by remember { mutableStateOf(StatisticsRange.MONTH) }
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedYear by remember { mutableStateOf(LocalDate.now().year) }
    var endYear by remember { mutableStateOf(LocalDate.now().year) }
    val locale = Locale.forLanguageTag("pl-PL")

    val filteredEntries = when (range) {
        StatisticsRange.MONTH -> entries.filter { YearMonth.from(it.date) == selectedMonth }
        StatisticsRange.YEAR -> entries.filter { it.date.year == selectedYear }
        StatisticsRange.YEARS -> entries.filter { it.date.year in (endYear - 4)..endYear }
    }
    val workEntries = filteredEntries.filter { it.type == WorkDayType.WORK }
    val totalMinutes = workEntries.sumOf { it.workedMinutes }
    val totalEarningsCents = calculateEntriesEarningsCents(workEntries, hourlyRateCents)
    val showEarnings = hourlyRateCents > 0 || workEntries.any { it.hourlyRateOverrideCents != null }
    val target = workEntries.size * dailyTargetMinutes
    val average = if (workEntries.isEmpty()) 0 else totalMinutes / workEntries.size

    val chartPoints = remember(range, selectedMonth, selectedYear, endYear, entries) {
        when (range) {
            StatisticsRange.MONTH -> {
                (1..6).map { week ->
                    val minutes = entries.filter {
                        YearMonth.from(it.date) == selectedMonth && ((it.date.dayOfMonth - 1) / 7 + 1) == week
                    }.sumOf { it.workedMinutes }
                    ChartPoint("T$week", minutes)
                }.dropLastWhile { it.minutes == 0 }
            }
            StatisticsRange.YEAR -> {
                (1..12).map { month ->
                    val minutes = entries.filter { it.date.year == selectedYear && it.date.monthValue == month }
                        .sumOf { it.workedMinutes }
                    val label = YearMonth.of(selectedYear, month).atDay(1)
                        .format(DateTimeFormatter.ofPattern("MMM", locale)).removeSuffix(".")
                    ChartPoint(label, minutes)
                }
            }
            StatisticsRange.YEARS -> {
                ((endYear - 4)..endYear).map { year ->
                    ChartPoint(year.toString(), entries.filter { it.date.year == year }.sumOf { it.workedMinutes })
                }
            }
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
        Text("ANALIZA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text("Podsumowania", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            "Miesiące, lata i pełna historia pracy",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatisticsRange.entries.forEach { option ->
                FilterChip(
                    selected = range == option,
                    onClick = { range = option },
                    label = { Text(option.label) }
                )
            }
        }

        PeriodNavigator(
            label = when (range) {
                StatisticsRange.MONTH -> selectedMonth.atDay(1)
                    .format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { it.titlecase(locale) }
                StatisticsRange.YEAR -> selectedYear.toString()
                StatisticsRange.YEARS -> "${endYear - 4}–$endYear"
            },
            onPrevious = {
                when (range) {
                    StatisticsRange.MONTH -> selectedMonth = selectedMonth.minusMonths(1)
                    StatisticsRange.YEAR -> selectedYear -= 1
                    StatisticsRange.YEARS -> endYear -= 5
                }
            },
            onNext = {
                when (range) {
                    StatisticsRange.MONTH -> selectedMonth = selectedMonth.plusMonths(1)
                    StatisticsRange.YEAR -> selectedYear += 1
                    StatisticsRange.YEARS -> endYear += 5
                }
            }
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("ŁĄCZNY CZAS", style = MaterialTheme.typography.labelMedium)
                Text(formatMinutes(totalMinutes), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                if (showEarnings) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Szacowana wypłata", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            formatMoney(totalEarningsCents),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Metric("Dni pracy", workEntries.size.toString())
                    Metric("Średnio", formatMinutes(average, compact = true))
                    val balance = totalMinutes - target
                    Metric("Bilans", if (balance >= 0) "+${formatMinutes(balance, true)}" else "−${formatMinutes(-balance, true)}")
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("Przepracowane godziny", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        WorkBarChart(points = chartPoints)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PeriodNavigator(label: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrevious) { Icon(Icons.Outlined.ChevronLeft, contentDescription = "Poprzedni okres") }
        Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onNext) { Icon(Icons.Outlined.ChevronRight, contentDescription = "Następny okres") }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun WorkBarChart(points: List<ChartPoint>) {
    val nonEmpty = points.any { it.minutes > 0 }
    if (!nonEmpty) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Brak danych w tym okresie",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val maxValue = points.maxOf { it.minutes }.coerceAtLeast(1)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(210.dp).padding(horizontal = 10.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            points.forEach { point ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    if (point.minutes > 0) {
                        Text(
                            (point.minutes / 60).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.62f)
                            .height((130f * point.minutes / maxValue).coerceAtLeast(3f).dp),
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                    ) {}
                    Spacer(Modifier.height(6.dp))
                    Text(point.label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
    }
}
