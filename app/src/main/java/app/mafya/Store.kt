package app.mafya

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Всё хранится локально в SharedPreferences: настройки, память, дневник, история, напоминания. */
class Store(ctx: Context) {
    companion object {
        const val DEFAULT_BASE = "https://openrouter.ai/api/v1"
        const val DEFAULT_MODEL = "google/gemini-2.5-flash"
    }

    private val p = ctx.getSharedPreferences("mafya", Context.MODE_PRIVATE)

    var apiBase: String
        get() = p.getString("base", DEFAULT_BASE) ?: DEFAULT_BASE
        set(v) { p.edit().putString("base", v.trim().trimEnd('/').ifBlank { DEFAULT_BASE }).apply() }

    var apiKey: String
        get() = (p.getString("key", "") ?: "").ifBlank { BuildConfig.OPENROUTER_KEY }
        set(v) { p.edit().putString("key", v.trim()).apply() }

    var model: String
        get() = p.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(v) { p.edit().putString("model", v.trim().ifBlank { DEFAULT_MODEL }).apply() }

    var userName: String
        get() = p.getString("name", "") ?: ""
        set(v) { p.edit().putString("name", v.trim()).apply() }

    var speak: Boolean
        get() = p.getBoolean("speak", true)
        set(v) { p.edit().putBoolean("speak", v).apply() }

    // ---------- Память ----------
    fun memories(): MutableList<String> {
        val a = JSONArray(p.getString("mem", "[]"))
        return MutableList(a.length()) { a.getString(it) }
    }

    fun addMemory(s: String) {
        val l = memories()
        if (s.isNotBlank() && l.none { it.equals(s, ignoreCase = true) }) {
            l.add(s.trim())
            p.edit().putString("mem", JSONArray(l).toString()).apply()
        }
    }

    fun removeMemory(index: Int) {
        val l = memories()
        if (index in l.indices) {
            l.removeAt(index)
            p.edit().putString("mem", JSONArray(l).toString()).apply()
        }
    }

    // ---------- Дневник ----------
    data class DiaryEntry(val date: String, val text: String)

    fun diary(): List<DiaryEntry> {
        val a = JSONArray(p.getString("diary", "[]"))
        return List(a.length()) {
            val o = a.getJSONObject(it)
            DiaryEntry(o.getString("d"), o.getString("t"))
        }
    }

    fun addDiary(date: String, text: String) {
        val a = JSONArray(p.getString("diary", "[]"))
        a.put(JSONObject().put("d", date).put("t", text))
        p.edit().putString("diary", a.toString()).apply()
    }

    // ---------- История диалога ----------
    data class Msg(val role: String, val content: String)

    fun history(): List<Msg> {
        val a = JSONArray(p.getString("hist", "[]"))
        return List(a.length()) {
            val o = a.getJSONObject(it)
            Msg(o.getString("r"), o.getString("c"))
        }
    }

    fun addHistory(role: String, content: String) {
        val all = (history() + Msg(role, content)).takeLast(30)
        val a = JSONArray()
        all.forEach { a.put(JSONObject().put("r", it.role).put("c", it.content)) }
        p.edit().putString("hist", a.toString()).apply()
    }

    // ---------- Напоминания ----------
    data class Reminder(val time: Long, val text: String)

    fun reminders(): List<Reminder> {
        val a = JSONArray(p.getString("rem", "[]"))
        val now = System.currentTimeMillis()
        return List(a.length()) {
            val o = a.getJSONObject(it)
            Reminder(o.getLong("t"), o.getString("x"))
        }.filter { it.time > now }.sortedBy { it.time }
    }

    fun addReminder(time: Long, text: String) {
        val all = reminders() + Reminder(time, text)
        val a = JSONArray()
        all.forEach { a.put(JSONObject().put("t", it.time).put("x", it.text)) }
        p.edit().putString("rem", a.toString()).apply()
    }
}
