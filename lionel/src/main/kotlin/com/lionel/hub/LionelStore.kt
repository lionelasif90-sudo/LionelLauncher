package com.lionel.hub

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Small on-device store: launch statistics (for smart suggestions), focus mode,
 * locked apps and the optional AI key. Nothing here leaves the device except the
 * AI key + typed command when the user chooses to use the AI command bar.
 */
class LionelStore private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Any()
    private var counts: MutableMap<String, IntArray>? = null

    // ---------- launch statistics ----------

    private fun loadCounts(): MutableMap<String, IntArray> {
        counts?.let { return it }
        val map = HashMap<String, IntArray>()
        val raw = prefs.getString(KEY_COUNTS, null)
        if (raw != null) {
            try {
                val obj = JSONObject(raw)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val pkg = keys.next()
                    val arr = obj.getJSONArray(pkg)
                    val buckets = IntArray(HOURS)
                    for (i in 0 until minOf(HOURS, arr.length())) buckets[i] = arr.optInt(i)
                    map[pkg] = buckets
                }
            } catch (e: Exception) {
                map.clear()
            }
        }
        counts = map
        return map
    }

    private fun persistCounts(map: Map<String, IntArray>) {
        val obj = JSONObject()
        for ((pkg, buckets) in map) {
            val arr = JSONArray()
            for (v in buckets) arr.put(v)
            obj.put(pkg, arr)
        }
        prefs.edit().putString(KEY_COUNTS, obj.toString()).apply()
    }

    fun recordLaunch(packageName: String, nowMillis: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            val map = loadCounts()
            val hour = hourOf(nowMillis)
            val buckets = map.getOrPut(packageName) { IntArray(HOURS) }
            buckets[hour] = buckets[hour] + 1
            if (buckets[hour] > MAX_BUCKET) {
                for (i in buckets.indices) buckets[i] = buckets[i] / 2
            }
            persistCounts(map)
        }
    }

    /** Package names most likely to be wanted right now (time-of-day aware). */
    fun suggestions(
        limit: Int,
        nowMillis: Long = System.currentTimeMillis(),
        exclude: Set<String> = emptySet(),
    ): List<String> {
        val snapshot = synchronized(lock) { loadCounts().mapValues { it.value.copyOf() } }
        val hour = hourOf(nowMillis)
        val prev = (hour + HOURS - 1) % HOURS
        val next = (hour + 1) % HOURS
        return snapshot.entries
            .filter { it.key !in exclude }
            .map { (pkg, b) ->
                pkg to (3.0 * b[hour] + 1.5 * (b[prev] + b[next]) + 0.1 * b.sum())
            }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    private fun hourOf(millis: Long): Int {
        val c = Calendar.getInstance()
        c.timeInMillis = millis
        return c.get(Calendar.HOUR_OF_DAY)
    }

    // ---------- focus mode ----------

    /** 0 = off, Long.MAX_VALUE = until the user stops it. */
    var focusUntil: Long
        get() = prefs.getLong(KEY_FOCUS_UNTIL, 0L)
        set(value) {
            prefs.edit().putLong(KEY_FOCUS_UNTIL, value).apply()
        }

    val isFocusOn: Boolean
        get() = System.currentTimeMillis() < focusUntil

    var focusApps: Set<String>
        get() = HashSet(prefs.getStringSet(KEY_FOCUS_APPS, emptySet()) ?: emptySet())
        set(value) {
            prefs.edit().putStringSet(KEY_FOCUS_APPS, HashSet(value)).apply()
        }

    // ---------- app lock ----------

    var lockedApps: Set<String>
        get() = HashSet(prefs.getStringSet(KEY_LOCKED_APPS, emptySet()) ?: emptySet())
        set(value) {
            prefs.edit().putStringSet(KEY_LOCKED_APPS, HashSet(value)).apply()
        }

    // ---------- AI ----------

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_API_KEY, value).apply()
        }

    companion object {
        private const val PREFS = "lionel_store"
        private const val KEY_COUNTS = "launch_counts"
        private const val KEY_FOCUS_UNTIL = "focus_until"
        private const val KEY_FOCUS_APPS = "focus_apps"
        private const val KEY_LOCKED_APPS = "locked_apps"
        private const val KEY_API_KEY = "api_key"
        private const val HOURS = 24
        private const val MAX_BUCKET = 500

        @Volatile
        private var instance: LionelStore? = null

        fun get(context: Context): LionelStore = instance ?: synchronized(this) {
            instance ?: LionelStore(context).also { instance = it }
        }
    }
}
