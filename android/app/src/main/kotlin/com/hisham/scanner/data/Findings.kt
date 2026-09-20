package com.hisham.scanner.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Finding(
    val id: String,
    val kind: String,      // "sweep" | "scan" | "network"
    val zone: String,
    val title: String,
    val note: String,
    val at: Long
)

/**
 * Everything the user flags, kept on the device and nowhere else.
 *
 * Backed by SharedPreferences and excluded from cloud backup and device
 * transfer in data_extraction_rules.xml, because a list of suspected cameras
 * in someone's room is not something to sync anywhere.
 */
class FindingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("scanner.findings", Context.MODE_PRIVATE)

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Finding>> = _items.asStateFlow()

    private val _sweep = MutableStateFlow(loadSweep())
    /** key is "zoneId:index", value is "ok" or "flag". */
    val sweep: StateFlow<Map<String, String>> = _sweep.asStateFlow()

    private fun load(): List<Finding> {
        val raw = prefs.getString(KEY_FINDINGS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Finding(
                    id = o.getString("id"),
                    kind = o.optString("kind", "sweep"),
                    zone = o.optString("zone", ""),
                    title = o.optString("title", ""),
                    note = o.optString("note", ""),
                    at = o.optLong("at", 0L)
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun persist(list: List<Finding>) {
        val arr = JSONArray()
        list.forEach { f ->
            arr.put(
                JSONObject()
                    .put("id", f.id)
                    .put("kind", f.kind)
                    .put("zone", f.zone)
                    .put("title", f.title)
                    .put("note", f.note)
                    .put("at", f.at)
            )
        }
        prefs.edit().putString(KEY_FINDINGS, arr.toString()).apply()
        _items.value = list
    }

    fun add(kind: String, zone: String, title: String, note: String) {
        // Flagging the same checkpoint twice should not produce two entries.
        if (_items.value.any { it.kind == kind && it.zone == zone && it.title == title }) return
        val f = Finding(
            id = "f" + System.currentTimeMillis().toString(36) + "-" + (_items.value.size),
            kind = kind, zone = zone, title = title, note = note,
            at = System.currentTimeMillis()
        )
        persist(_items.value + f)
    }

    fun remove(id: String) = persist(_items.value.filterNot { it.id == id })

    fun removeMatching(kind: String, zone: String, title: String) =
        persist(_items.value.filterNot { it.kind == kind && it.zone == zone && it.title == title })

    fun clear() = persist(emptyList())

    // ---- sweep progress ----

    private fun loadSweep(): Map<String, String> {
        val raw = prefs.getString(KEY_SWEEP, null) ?: return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
    }

    fun setSweepMark(key: String, mark: String?) {
        val next = _sweep.value.toMutableMap()
        if (mark == null) next.remove(key) else next[key] = mark
        val o = JSONObject()
        next.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString(KEY_SWEEP, o.toString()).apply()
        _sweep.value = next
    }

    fun clearSweep() {
        prefs.edit().remove(KEY_SWEEP).apply()
        _sweep.value = emptyMap()
        persist(_items.value.filterNot { it.kind == "sweep" })
    }

    // ---- identity for the written record ----

    var reporterName: String
        get() = prefs.getString(KEY_NAME, "") ?: ""
        set(v) = prefs.edit().putString(KEY_NAME, v).apply()

    var placeName: String
        get() = prefs.getString(KEY_PLACE, "") ?: ""
        set(v) = prefs.edit().putString(KEY_PLACE, v).apply()

    companion object {
        private const val KEY_FINDINGS = "findings"
        private const val KEY_SWEEP = "sweep"
        private const val KEY_NAME = "reporter"
        private const val KEY_PLACE = "place"
    }
}

object ReportWriter {

    // Built per call rather than held in a field: a formatter that captures
    // Locale.getDefault() at class-init keeps whatever locale the app first
    // launched with, even after the user changes it.
    private fun stamp() = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    private fun dateOnly() = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun time(millis: Long): String = stamp().format(Date(millis))

    /**
     * A contemporaneous, specific account. Not a forensic finding and not legal
     * advice - but far better than describing it from memory at a counter.
     */
    fun compose(findings: List<Finding>, who: String, place: String): String {
        val name = who.ifBlank { "[your name]" }
        val where = place.ifBlank { "[address / name of the place]" }
        val now = Date()
        val b = StringBuilder()

        b.appendLine("RECORD OF A HIDDEN-CAMERA SEARCH")
        b.appendLine()
        b.appendLine("Prepared by : $name")
        b.appendLine("Location    : $where")
        b.appendLine("Prepared on : ${stamp().format(now)}")
        b.appendLine()
        b.appendLine("I carried out a physical and optical search of the above premises.")
        b.appendLine("The following observations were recorded at the times shown.")
        b.appendLine()
        b.appendLine("OBSERVATIONS")
        b.appendLine("------------")

        findings.forEachIndexed { i, f ->
            b.appendLine()
            b.appendLine("${i + 1}. ${f.title}")
            b.appendLine("   Area     : ${f.zone}")
            b.appendLine("   Recorded : ${time(f.at)}")
            if (f.note.isNotBlank()) b.appendLine("   Detail   : ${f.note}")
        }

        b.appendLine()
        b.appendLine()
        b.appendLine("STATEMENT")
        b.appendLine("---------")
        b.appendLine()
        b.appendLine("To the Station House Officer / Cyber Cell,")
        b.appendLine()
        b.appendLine("I wish to report the suspected presence of a concealed recording device at")
        b.appendLine("$where. The observations listed above were made by me on ${dateOnly().format(now)}.")
        b.appendLine()
        b.appendLine("I request that the premises be inspected and that any device found be seized")
        b.appendLine("and examined. I believe the matter attracts Section 77 of the Bharatiya Nyaya")
        b.appendLine("Sanhita, 2023 (voyeurism) and Section 66E of the Information Technology Act,")
        b.appendLine("2000 (violation of privacy).")
        b.appendLine()
        b.appendLine("I have not moved, switched off or interfered with anything I suspected, so")
        b.appendLine("that the scene and any stored footage remain intact.")
        b.appendLine()
        b.appendLine("Signature : ______________________")
        b.appendLine("Name      : $name")
        b.appendLine("Phone     : ______________________")
        b.appendLine("Date      : ${dateOnly().format(now)}")
        b.appendLine()
        b.appendLine("---")
        b.appendLine("Observations recorded with Scanner. This is a personal record written by the")
        b.appendLine("person named above, not a forensic finding, and not legal advice.")

        return b.toString()
    }
}
