package pl.godzinypracy.workly.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.godzinypracy.workly.data.WorkDayType
import pl.godzinypracy.workly.data.WorkEntry
import pl.godzinypracy.workly.data.calculateEntryEarningsCents
import pl.godzinypracy.workly.data.formatMoneyCompact
import pl.godzinypracy.workly.data.calculateEntriesEarningsCents
import pl.godzinypracy.workly.data.formatMoney
import pl.godzinypracy.workly.data.formatMinutes
import pl.godzinypracy.workly.ui.WorkUiState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CalendarScreen(
    modifier: Modifier = Modifier,
    state: WorkUiState,
    onMonthChange: (YearMonth) -> Unit,
    onDayClick: (LocalDate) -> Unit
) {
    val monthEntries = remember(state.visibleMonth, state.entries) {
        state.entries.values.filter { YearMonth.from(it.date) == state.visibleMonth }
    }
    val totalMinutes = monthEntries.sumOf { it.workedMinutes }
    val workEntries = monthEntries.filter { it.type == WorkDayType.WORK }
    val totalEarningsCents = calculateEntriesEarningsCents(workEntries, state.hourlyRateCents)
    val showEarnings = state.hourlyRateCents > 0 || workEntries.any { it.hourlyRateOverrideCents != null }
    val targetMinutes = workEntries.size * state.dailyTargetMinutes
    val balance = totalMinutes - targetMinutes

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "GODZINY PRACY",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Kalendarz",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Wybierz dzień, aby wpisać godziny od–do",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))
        MonthSummaryCard(
            totalMinutes = totalMinutes,
            workDays = workEntries.size,
            balanceMinutes = balance,
            targetMinutes = targetMinutes,
            totalEarningsCents = totalEarningsCents,
            showEarnings = showEarnings
        )

        Spacer(Modifier.height(18.dp))
        MonthHeader(
            month = state.visibleMonth,
            onPrevious = { onMonthChange(state.visibleMonth.minusMonths(1)) },
            onNext = { onMonthChange(state.visibleMonth.plusMonths(1)) }
        )
        CalendarMonthGrid(
            month = state.visibleMonth,
            entries = state.entries,
            hourlyRateCents = state.hourlyRateCents,
            selectedDate = state.selectedDate,
            onDayClick = onDayClick
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.TouchApp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (monthEntries.isEmpty()) {
                    "Dotknij dnia, aby dodać pierwszy wpis"
                } else {
                    "Dotknij dnia, aby dodać lub edytować wpis"
                },
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun MonthSummaryCard(
    totalMinutes: Int,
    workDays: Int,
    balanceMinutes: Int,
    targetMinutes: Int,
    totalEarningsCents: Long,
    showEarnings: Boolean
) {
    val progress = if (targetMinutes > 0) {
        (totalMinutes.toFloat() / targetMinutes).coerceIn(0f, 1f)
    } else 0f

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("PRZEPRACOWANO W MIESIĄCU", style = MaterialTheme.typography.labelMedium)
            Text(
                text = formatMinutes(totalMinutes),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("$workDays dni pracy", style = MaterialTheme.typography.bodyMedium)
                val balanceText = when {
                    targetMinutes == 0 -> "Brak normy"
                    balanceMinutes > 0 -> "+${formatMinutes(balanceMinutes)}"
                    balanceMinutes < 0 -> "−${formatMinutes(-balanceMinutes)}"
                    else -> "Bilans 0 godz."
                }
                Text(balanceText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            if (showEarnings) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Szacowana wypłata", style = MaterialTheme.typography.titleMedium)
                    Text(
                        formatMoney(totalEarningsCents),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val locale = Locale.forLanguageTag("pl-PL")
    val label = month.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        .replaceFirstChar { it.titlecase(locale) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = "Poprzedni miesiąc")
        }
        Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onNext) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = "Następny miesiąc")
        }
    }
}

@Composable
private fun CalendarMonthGrid(
    month: YearMonth,
    entries: Map<LocalDate, WorkEntry>,
    hourlyRateCents: Int,
    selectedDate: LocalDate,
    onDayClick: (LocalDate) -> Unit
) {
    val daysOfWeek = listOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
    )
    val dayLabels = listOf("Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd")
    val firstDate = month.atDay(1)
    val offset = firstDate.dayOfWeek.value - DayOfWeek.MONDAY.value
    val gridStart = firstDate.minusDays(offset.toLong())
    val dates = remember(month) { List(42) { gridStart.plusDays(it.toLong()) } }

    Row(Modifier.fillMaxWidth()) {
        daysOfWeek.forEachIndexed { index, _ ->
            Text(
                text = dayLabels[index],
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    dates.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth()) {
            week.forEach { date ->
                CalendarDay(
                    modifier = Modifier.weight(1f),
                    date = date,
                    isCurrentMonth = YearMonth.from(date) == month,
                    isSelected = date == selectedDate,
                    isToday = date == LocalDate.now(),
                    entry = entries[date],
                    hourlyRateCents = hourlyRateCents,
                    onClick = { onDayClick(date) }
                )
            }
        }
    }
}

@Composable
private fun CalendarDay(
    modifier: Modifier,
    date: LocalDate,
    isCurrentMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    entry: WorkEntry?,
    hourlyRateCents: Int,
    onClick: () -> Unit
) {
    val background = when {
        isSelected -> MaterialTheme.colorScheme.primary
        entry != null -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }
    val foreground = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        entry != null -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val border = if (isToday && !isSelected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else null

    Box(modifier = modifier.padding(2.dp)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.76f)
                .alpha(if (isCurrentMonth) 1f else 0.42f)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(12.dp),
            color = background,
            contentColor = foreground,
            border = border
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(vertical = 5.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium
                )
                Text(
                    text = when (entry?.type) {
                        WorkDayType.WORK -> formatMinutes(entry.workedMinutes, compact = true)
                        WorkDayType.VACATION -> "Urlop"
                        WorkDayType.SICK_LEAVE -> "L4"
                        WorkDayType.DAY_OFF -> "Wolne"
                        null -> ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
                if (
                    entry?.type == WorkDayType.WORK &&
                    (hourlyRateCents > 0 || entry.hourlyRateOverrideCents != null)
                ) {
                    Text(
                        text = formatMoneyCompact(calculateEntryEarningsCents(entry, hourlyRateCents)),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
