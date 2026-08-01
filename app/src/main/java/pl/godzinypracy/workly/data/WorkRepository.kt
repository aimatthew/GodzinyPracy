package pl.godzinypracy.workly.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class WorkRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun loadEntries(): List<WorkEntry> {
        val raw = preferences.getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                repeat(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    add(
                        WorkEntry(
                            date = LocalDate.parse(item.getString("date")),
                            startMinutes = item.optInt("start", 8 * 60),
                            endMinutes = item.optInt("end", 16 * 60),
                            breakMinutes = item.optInt("break", 0),
                            type = runCatching {
                                WorkDayType.valueOf(item.optString("type", WorkDayType.WORK.name))
                            }.getOrDefault(WorkDayType.WORK),
                            hourlyRateOverrideCents = if (item.has("hourlyRateCents") && !item.isNull("hourlyRateCents")) {
                                item.optInt("hourlyRateCents", 0).coerceIn(0, 1_000_000)
                            } else {
                                null
                            },
                            note = item.optString("note", "")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveEntries(entries: Collection<WorkEntry>) {
        val array = JSONArray()
        entries.sortedBy { it.date }.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("date", entry.date.toString())
                    put("start", entry.startMinutes)
                    put("end", entry.endMinutes)
                    put("break", entry.breakMinutes)
                    put("type", entry.type.name)
                    put("note", entry.note)
                    entry.hourlyRateOverrideCents?.let { put("hourlyRateCents", it) }
                }
            )
        }
        preferences.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    fun loadDailyTargetMinutes(): Int = preferences.getInt(KEY_DAILY_TARGET, 8 * 60)

    fun saveDailyTargetMinutes(minutes: Int) {
        preferences.edit().putInt(KEY_DAILY_TARGET, minutes.coerceIn(60, 24 * 60)).apply()
    }

    fun loadHourlyRateCents(): Int = preferences.getInt(KEY_HOURLY_RATE, 0)

    fun saveHourlyRateCents(cents: Int) {
        preferences.edit().putInt(KEY_HOURLY_RATE, cents.coerceIn(0, 1_000_000)).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "workly_local_data"
        private const val KEY_ENTRIES = "entries_v1"
        private const val KEY_DAILY_TARGET = "daily_target_minutes"
        private const val KEY_HOURLY_RATE = "hourly_rate_cents"
    }
}
