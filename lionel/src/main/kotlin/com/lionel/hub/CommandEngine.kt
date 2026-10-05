package com.lionel.hub

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed interface LionelAction {
    data class OpenApp(val app: AppEntry) : LionelAction
    data class WebSearch(val query: String) : LionelAction
    data class SetAlarm(val hour: Int, val minute: Int) : LionelAction
    data class SetTimer(val seconds: Int) : LionelAction
    data class Dial(val number: String) : LionelAction
    data class OpenSettings(val kind: String) : LionelAction
    data class Answer(val text: String) : LionelAction
}

data class CommandResult(val message: String, val action: LionelAction?)

/**
 * Understands typed or spoken commands. Simple English commands are parsed on the device;
 * anything else goes to the AI (only if the user saved an API key), else a web search.
 */
class CommandEngine(
    private val context: Context,
    private val apps: List<AppEntry>,
) {
    suspend fun interpret(input: String): CommandResult {
        val text = input.trim()
        parseLocal(text)?.let { return CommandResult(describe(it), it) }

        val key = LionelStore.get(context).apiKey
        if (key.isNotBlank()) {
            val ai = askAi(text, key)
            if (ai.action != null) return ai
            // AI failed: fall through to web search but keep the error visible
            val search = LionelAction.WebSearch(text)
            return CommandResult("${ai.message}. Searching the web instead.", search)
        }
        val search = LionelAction.WebSearch(text)
        return CommandResult(describe(search), search)
    }

    fun execute(action: LionelAction) {
        when (action) {
            is LionelAction.OpenApp -> InstalledApps.launch(context, action.app)
            is LionelAction.WebSearch -> start(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(action.query))),
            )
            is LionelAction.SetAlarm -> start(
                Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, action.hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
            )
            is LionelAction.SetTimer -> start(
                Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, action.seconds)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
            )
            is LionelAction.Dial -> start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(action.number))))
            is LionelAction.OpenSettings -> start(Intent(settingsAction(action.kind)))
            is LionelAction.Answer -> Unit
        }
    }

    private fun start(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app can handle this", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(context, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun describe(a: LionelAction): String = when (a) {
        is LionelAction.OpenApp -> "Opening ${a.app.label}"
        is LionelAction.WebSearch -> "Searching the web for “${a.query}”"
        is LionelAction.SetAlarm -> "Alarm set for %02d:%02d".format(a.hour, a.minute)
        is LionelAction.SetTimer -> "Timer set for ${a.seconds / 60} min ${a.seconds % 60} s"
        is LionelAction.Dial -> "Dialing ${a.number}"
        is LionelAction.OpenSettings -> "Opening ${a.kind} settings (Android doesn't let apps flip it directly)"
        is LionelAction.Answer -> a.text
    }

    // ---------------- local parsing ----------------

    private fun parseLocal(text: String): LionelAction? {
        val lower = text.lowercase()

        Regex("^(open|launch|start)\\s+(.+)$").find(lower)?.let { m ->
            bestApp(m.groupValues[2], 40)?.let { return LionelAction.OpenApp(it) }
        }
        Regex("^(call|dial)\\s+([+0-9][0-9 \\-]{2,})$").find(lower)?.let { m ->
            return LionelAction.Dial(m.groupValues[2].replace(" ", "").replace("-", ""))
        }
        if (lower.contains("alarm")) {
            parseTime(lower)?.let { (h, m) -> return LionelAction.SetAlarm(h, m) }
        }
        if (lower.contains("timer")) {
            Regex("(\\d+)\\s*(hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)\\b").find(lower)?.let { m ->
                val n = m.groupValues[1].toIntOrNull() ?: return@let
                val unit = m.groupValues[2]
                val seconds = when {
                    unit.startsWith("h") -> n * 3600
                    unit.startsWith("m") -> n * 60
                    else -> n
                }
                if (seconds > 0) return LionelAction.SetTimer(seconds)
            }
        }
        Regex("^(search|google|find|look up)\\s+(.+)$").find(lower)?.let { m ->
            return LionelAction.WebSearch(m.groupValues[2])
        }
        if (Regex("\\b(turn|toggle|enable|disable|settings?)\\b").containsMatchIn(lower)) {
            val kind = when {
                lower.contains("wifi") || lower.contains("wi-fi") -> "wifi"
                lower.contains("bluetooth") -> "bluetooth"
                lower.contains("airplane") || lower.contains("flight mode") -> "airplane"
                lower.contains("brightness") || lower.contains("display") -> "display"
                lower.contains("sound") || lower.contains("volume") -> "sound"
                lower.contains("location") || lower.contains("gps") -> "location"
                lower.contains("battery") -> "battery"
                else -> null
            }
            if (kind != null) return LionelAction.OpenSettings(kind)
        }
        if (lower.split(' ').size <= 3) {
            bestApp(lower, 60)?.let { return LionelAction.OpenApp(it) }
        }
        return null
    }

    private fun parseTime(lower: String): Pair<Int, Int>? {
        val m = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(lower) ?: return null
        var h = m.groupValues[1].toIntOrNull() ?: return null
        val min = m.groupValues[2].toIntOrNull() ?: 0
        val ampm = m.groupValues[3]
        if (ampm == "pm" && h < 12) h += 12
        if (ampm == "am" && h == 12) h = 0
        return if (h in 0..23 && min in 0..59) h to min else null
    }

    private fun bestApp(query: String, minScore: Int): AppEntry? {
        var best: AppEntry? = null
        var bestScore = 0
        for (a in apps) {
            val s = Fuzzy.score(query, a.label)
            if (s > bestScore) {
                bestScore = s
                best = a
            }
        }
        return if (bestScore >= minScore) best else null
    }

    private fun settingsAction(kind: String): String = when (kind) {
        "wifi" -> Settings.ACTION_WIFI_SETTINGS
        "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
        "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
        "display" -> Settings.ACTION_DISPLAY_SETTINGS
        "sound" -> Settings.ACTION_SOUND_SETTINGS
        "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
        "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
        else -> Settings.ACTION_SETTINGS
    }

    // ---------------- AI (optional) ----------------

    private suspend fun askAi(text: String, key: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject()
                .put("model", MODEL)
                .put("max_tokens", 300)
                .put("system", SYSTEM_PROMPT)
                .put(
                    "messages",
                    JSONArray().put(JSONObject().put("role", "user").put("content", text)),
                )
            val conn = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.doOutput = true
            conn.setRequestProperty("content-type", "application/json")
            conn.setRequestProperty("x-api-key", key)
            conn.setRequestProperty("anthropic-version", "2023-06-01")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val resp = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                CommandResult("AI error ($code)", null)
            } else {
                val reply = JSONObject(resp).getJSONArray("content").getJSONObject(0).getString("text")
                parseAi(reply)
            }
        } catch (e: Exception) {
            CommandResult("AI unavailable (${e.javaClass.simpleName})", null)
        }
    }

    private fun parseAi(raw: String): CommandResult {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) {
            val a = LionelAction.Answer(raw.trim())
            return CommandResult(a.text, a)
        }
        val o = try {
            JSONObject(raw.substring(start, end + 1))
        } catch (e: Exception) {
            val a = LionelAction.Answer(raw.trim())
            return CommandResult(a.text, a)
        }
        val action: LionelAction? = when (o.optString("action")) {
            "open_app" -> bestApp(o.optString("app"), 40)?.let { LionelAction.OpenApp(it) }
            "web_search" -> LionelAction.WebSearch(o.optString("query"))
            "set_alarm" -> LionelAction.SetAlarm(o.optInt("hour"), o.optInt("minute"))
            "set_timer" -> LionelAction.SetTimer(o.optInt("seconds"))
            "dial" -> LionelAction.Dial(o.optString("number"))
            "open_settings" -> LionelAction.OpenSettings(o.optString("setting", "general"))
            else -> LionelAction.Answer(o.optString("text", raw.trim()))
        }
        return if (action == null) {
            CommandResult("I couldn't find that app", LionelAction.Answer("I couldn't find that app"))
        } else {
            CommandResult(describe(action), action)
        }
    }

    private companion object {
        const val MODEL = "claude-haiku-4-5-20251001"
        const val SYSTEM_PROMPT =
            "You are the command parser of an Android launcher. The user may write in any language, " +
                "including Bengali. Reply with ONLY one JSON object, no markdown. Schema: " +
                "{\"action\":\"open_app|web_search|set_alarm|set_timer|dial|open_settings|answer\"," +
                "\"app\":string,\"query\":string,\"hour\":int,\"minute\":int,\"seconds\":int," +
                "\"number\":string,\"setting\":\"wifi|bluetooth|airplane|display|sound|location|battery|general\"," +
                "\"text\":string}. Include only the fields needed. Use action \"answer\" with a short " +
                "reply in \"text\" when the user asks a question instead of giving a command."
    }
}
