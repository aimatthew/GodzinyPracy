package pl.godzinypracy.workly.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

object WorkBackupCodec {
    private const val SCHEMA_VERSION = 1

    fun encode(repository: WorkRepository): String {
        val entries = JSONArray()
        repository.loadEntries().sortedBy { it.date }.forEach { entry ->
            entries.put(
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

        return JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("createdAt", Instant.now().toString())
            .put("dailyTargetMinutes", repository.loadDailyTargetMinutes())
            .put("hourlyRateCents", repository.loadHourlyRateCents())
            .put("entries", entries)
            .toString()
    }

    fun decodeInto(repository: WorkRepository, raw: String) {
        val root = JSONObject(raw)
        val version = root.optInt("schemaVersion", -1)
        require(version in 1..SCHEMA_VERSION) { "Nieobsługiwana wersja kopii: $version" }

        val parsedEntries = buildList {
            val entries = root.optJSONArray("entries") ?: JSONArray()
            repeat(entries.length()) { index ->
                val item = entries.getJSONObject(index)
                add(
                    WorkEntry(
                        date = LocalDate.parse(item.getString("date")),
                        startMinutes = item.optInt("start", 8 * 60).coerceIn(0, 24 * 60 - 1),
                        endMinutes = item.optInt("end", 16 * 60).coerceIn(0, 24 * 60 - 1),
                        breakMinutes = item.optInt("break", 0).coerceIn(0, 24 * 60),
                        type = runCatching {
                            WorkDayType.valueOf(item.optString("type", WorkDayType.WORK.name))
                        }.getOrDefault(WorkDayType.WORK),
                        hourlyRateOverrideCents = if (
                            item.has("hourlyRateCents") && !item.isNull("hourlyRateCents")
                        ) {
                            item.optInt("hourlyRateCents", 0).coerceIn(0, 1_000_000)
                        } else {
                            null
                        },
                        note = item.optString("note", "").take(2_000)
                    )
                )
            }
        }.distinctBy { it.date }

        repository.saveEntries(parsedEntries)
        repository.saveDailyTargetMinutes(
            root.optInt("dailyTargetMinutes", 8 * 60).coerceIn(60, 24 * 60)
        )
        repository.saveHourlyRateCents(
            root.optInt("hourlyRateCents", 0).coerceIn(0, 1_000_000)
        )
    }
}
