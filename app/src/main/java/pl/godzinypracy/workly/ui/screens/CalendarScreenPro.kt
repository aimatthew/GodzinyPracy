package pl.godzinypracy.workly.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import pl.godzinypracy.workly.data.calculateEntriesEarningsCents
import pl.godzinypracy.workly.data.calculateEntryEarningsCents
import pl.godzinypracy.workly.data.formatMoney
import pl.godzinypracy.workly.data.formatMoneyCompact
import pl.godzinypracy.workly.data.formatMinutes
import pl.godzinypracy.workly.data.isPolishPublicHoliday
import pl.godzinypracy.workly.ui.WorkUiState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CalendarRed = Color(0xFFFF7474)

@Composable
fun CalendarScreenPro(
    modifier: Modifier = Modifier,
    state: WorkUiState,
    accountPhotoUrl: String?,
    onMonthChange: (YearMonth) -> Unit,
    onDayClick: (LocalDate) -> Unit,
    onSettingsClick: () -> Unit
) {
    val monthEntries = remember(state.visibleMonth, state.entries) {
        state.entries.values.filter { YearMonth.from(it.date) == state.visibleMonth }
    }
    val workEntries = monthEntries.filter { it.type == WorkDayType.WORK }
    val totalMinutes = workEntries.sumOf { it.workedMinutes }
    val targetMinutes = workEntries.size * state.dailyTargetMinutes
    val balanceMinutes = totalMinutes - targetMinutes
    val totalEarningsCents = calculateEntriesEarningsCents(workEntries, state.hourlyRateCents)
    val showEarnings = state.hourlyRateCents > 0 || workEntries.any { it.hourlyRateOverrideCents != null }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        CalendarTopBar(
            accountPhotoUrl = accountPhotoUrl,
            onSettingsClick = onSettingsClick
        )
        Spacer(Modifier.height(18.dp))
        ProMonthSummary(
            totalMinutes = totalMinutes,
            workDays = workEntries.size,
            balanceMinutes = balanceMinutes,
            targetMinutes = targetMinutes,
            totalEarningsCents = totalEarningsCents,
            showEarnings = showEarnings
        )
        Spacer(Modifier.height(18.dp))
        ProMonthNavigator(
            month = state.visibleMonth,
            onPrevious = { onMonthChange(state.visibleMonth.minusMonths(1)) },
            onNext = { onMonthChange(state.visibleMonth.plusMonths(1)) }
        )
        Spacer(Modifier.height(8.dp))
        ProCalendarGrid(
            month = state.visibleMonth,
            entries = state.entries,
            hourlyRateCents = state.hourlyRateCents,
            selectedDate = state.selectedDate,
            onDayClick = onDayClick
        )
        Spacer(Modifier.height(16.dp))
        CalendarLegend()
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun CalendarTopBar(
    accountPhotoUrl: String?,
    onSettingsClick: () -> Unit
) {
    val accountImage by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = accountPhotoUrl
    ) {
        value = accountPhotoUrl?.let { loadAccountImage(it) }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "GODZINY PRACY",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Kalendarz",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Dotknij dnia, aby dodać lub edytować wpis",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (accountImage != null) {
            IconButton(onClick = onSettingsClick) {
                Image(
                    bitmap = accountImage!!,
                    contentDescription = "Konto Google",
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
        } else {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Outlined.PersonOutline, contentDescription = "Otwórz ustawienia")
                }
            }
        }
    }
}
private suspend fun loadAccountImage(photoUrl: String): ImageBitmap? = withContext(Dispatchers.IO) {
    val connection = (URL(photoUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 15_000
        useCaches = true
    }
    try {
        if (connection.responseCode !in 200..299) return@withContext null
        connection.inputStream.use { stream ->
            BitmapFactory.decodeStream(stream)?.asImageBitmap()
        }
    } catch (_: Exception) {
        null
    } finally {
        connection.disconnect()
    }
}


@Composable
private fun ProMonthSummary(
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
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SummaryMetric(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
                    label = "PRZEPRACOWANO",
                    value = formatMinutes(totalMinutes)
                )
                Box(
                    Modifier
                        .padding(horizontal = 14.dp)
                        .width(1.dp)
                        .height(74.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                SummaryMetric(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null) },
                    label = "WYPŁATA",
                    value = if (showEarnings) formatMoney(totalEarningsCents) else "—"
                )
            }
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f)
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$workDays dni pracy", style = MaterialTheme.typography.bodySmall)
                val balance = when {
                    targetMinutes == 0 -> "Brak normy"
                    balanceMinutes > 0 -> "+${formatMinutes(balanceMinutes)}"
                    balanceMinutes < 0 -> "−${formatMinutes(-balanceMinutes)}"
                    else -> "Bilans 0 godz."
                }
                Text(balance, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SummaryMetric(
    modifier: Modifier,
    icon: @Composable () -> Unit,
    label: String,
    value: String
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.32f),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) { icon() }
            }
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(10.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun ProMonthNavigator(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    val locale = Locale.forLanguageTag("pl-PL")
    val label = month.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        .replaceFirstChar { it.titlecase(locale) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MonthArrow(Icons.Outlined.ChevronLeft, "Poprzedni miesiąc", onPrevious)
        Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        MonthArrow(Icons.Outlined.ChevronRight, "Następny miesiąc", onNext)
    }
}

@Composable
private fun MonthArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(42.dp)) {
            Icon(icon, contentDescription = description)
        }
    }
}

@Composable
private fun ProCalendarGrid(
    month: YearMonth,
    entries: Map<LocalDate, WorkEntry>,
    hourlyRateCents: Int,
    selectedDate: LocalDate,
    onDayClick: (LocalDate) -> Unit
) {
    val dayLabels = listOf("Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd")
    val firstDate = month.atDay(1)
    val gridStart = firstDate.minusDays((firstDate.dayOfWeek.value - 1).toLong())
    val dates = remember(month) { List(42) { gridStart.plusDays(it.toLong()) } }

    Row(Modifier.fillMaxWidth()) {
        dayLabels.forEachIndexed { index, label ->
            Text(
                label,
                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (index == 6) FontWeight.Bold else FontWeight.Medium,
                color = if (index == 6) CalendarRed else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    dates.chunked(7).forEach { week ->
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        Row(Modifier.fillMaxWidth()) {
            week.forEach { date ->
                ProCalendarDay(
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
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
}

@Composable
private fun ProCalendarDay(
    modifier: Modifier,
    date: LocalDate,
    isCurrentMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    entry: WorkEntry?,
    hourlyRateCents: Int,
    onClick: () -> Unit
) {
    val isSunday = date.dayOfWeek == DayOfWeek.SUNDAY
    val isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || isSunday
    val isHoliday = isPolishPublicHoliday(date)
    val isRedDay = isSunday || isHoliday
    val hasPay = entry?.type == WorkDayType.WORK &&
        (hourlyRateCents > 0 || entry.hourlyRateOverrideCents != null)

    val background = when {
        isSelected -> MaterialTheme.colorScheme.surfaceContainerHigh
        entry != null -> MaterialTheme.colorScheme.surfaceContainerHigh
        isWeekend -> MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.78f)
        else -> Color.Transparent
    }
    val border = when {
        isSelected -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        isToday -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.72f))
        entry != null -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        else -> null
    }
    val dayColor = when {
        isRedDay -> CalendarRed
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(modifier = modifier.padding(horizontal = 2.dp, vertical = 4.dp)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .alpha(if (isCurrentMonth) 1f else 0.38f)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(10.dp),
            color = background,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = border
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = dayColor,
                        fontWeight = if (isSelected || isToday || isRedDay) FontWeight.Bold else FontWeight.Medium
                    )
                    if (isToday) {
                        Spacer(Modifier.width(3.dp))
                        Box(Modifier.size(4.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    }
                }

                when (entry?.type) {
                    WorkDayType.WORK -> {
                        Text(formatMinutes(entry.workedMinutes, compact = true), style = MaterialTheme.typography.labelSmall)
                        if (hasPay) {
                            Text(
                                formatMoneyCompact(calculateEntryEarningsCents(entry, hourlyRateCents)),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                        Box(
                            Modifier
                                .fillMaxWidth(0.58f)
                                .height(3.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }
                    WorkDayType.VACATION -> Text("Urlop", style = MaterialTheme.typography.labelSmall)
                    WorkDayType.SICK_LEAVE -> Text("L4", style = MaterialTheme.typography.labelSmall)
                    WorkDayType.DAY_OFF -> Text("Wolne", style = MaterialTheme.typography.labelSmall)
                    null -> if (isSelected) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = "Dodaj wpis",
                                modifier = Modifier.size(22.dp).padding(3.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    } else {
                        Spacer(Modifier.height(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarLegend() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                LegendItem("Praca") {
                    Box(Modifier.width(28.dp).height(3.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                }
                LegendItem("Wybrany dzień") {
                    Surface(
                        modifier = Modifier.size(20.dp),
                        shape = RoundedCornerShape(5.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    ) {}
                }
            }
            LegendItem("Niedziele i święta") {
                Box(Modifier.size(8.dp).background(CalendarRed, CircleShape))
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, marker: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        marker()
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
