package com.lionel.hub

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.os.Build
import android.widget.Toast

data class AppEntry(
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val category: AppCategory,
)

enum class AppCategory(val title: String, val emoji: String) {
    SOCIAL("Social & chat", "💬"),
    GAME("Games", "🎮"),
    VIDEO("Video", "🎬"),
    AUDIO("Music & audio", "🎧"),
    IMAGE("Photos & camera", "🖼️"),
    NEWS("News & reading", "📰"),
    MAPS("Maps & travel", "🗺️"),
    FINANCE("Money", "💳"),
    PRODUCTIVITY("Productivity", "💼"),
    TOOLS("System & tools", "🛠️"),
    OTHER("Other", "📦"),
}

object InstalledApps {
    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .mapNotNull { ri ->
                val ai = ri.activityInfo ?: return@mapNotNull null
                if (ai.packageName == context.packageName) return@mapNotNull null
                val label = ri.loadLabel(pm).toString()
                AppEntry(
                    label = label,
                    packageName = ai.packageName,
                    component = ComponentName(ai.packageName, ai.name),
                    category = categorize(ai, label),
                )
            }
            .distinctBy { it.component }
            .sortedBy { it.label.lowercase() }
    }

    /** Opens the app through the launcher gate (focus mode, app lock, usage stats). */
    fun launch(context: Context, app: AppEntry) {
        if (!LionelGate.beforeLaunch(context, app.packageName)) return
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Can't open ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun categorize(ai: ActivityInfo, label: String): AppCategory {
        val raw = rawCategory(ai.applicationInfo)
        val fromSystem = when (raw) {
            0 -> AppCategory.GAME
            1 -> AppCategory.AUDIO
            2 -> AppCategory.VIDEO
            3 -> AppCategory.IMAGE
            4 -> AppCategory.SOCIAL
            5 -> AppCategory.NEWS
            6 -> AppCategory.MAPS
            7 -> AppCategory.PRODUCTIVITY
            else -> null
        }
        return fromSystem ?: guessByName(ai.packageName.lowercase() + " " + label.lowercase())
    }

    @Suppress("DEPRECATION")
    private fun rawCategory(info: ApplicationInfo?): Int {
        if (info == null) return -1
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return info.category
        return if (info.flags and ApplicationInfo.FLAG_IS_GAME != 0) 0 else -1
    }

    private fun guessByName(s: String): AppCategory {
        fun has(vararg words: String) = words.any { s.contains(it) }
        return when {
            has("whatsapp", "telegram", "facebook", "instagram", "messenger", "twitter", "discord",
                "snapchat", "reddit", "tiktok", "linkedin", "signal", "viber", "imo") -> AppCategory.SOCIAL
            has("youtube", "netflix", "video", "hotstar", "prime", "twitch") -> AppCategory.VIDEO
            has("spotify", "music", "audio", "podcast", "radio", "player") -> AppCategory.AUDIO
            has("camera", "gallery", "photo", "snapseed", "picture") -> AppCategory.IMAGE
            has("news", "reader", "kindle", "book", "pdf") -> AppCategory.NEWS
            has("maps", "uber", "pathao", "ola", "travel", "flight", "booking", "navigation") -> AppCategory.MAPS
            has("bank", "pay", "wallet", "bkash", "nagad", "finance", "money") -> AppCategory.FINANCE
            has("mail", "office", "docs", "drive", "calendar", "notes", "keep", "sheets", "slides",
                "word", "excel", "todo", "task") -> AppCategory.PRODUCTIVITY
            has("settings", "clock", "calculator", "dialer", "contacts", "files", "phone", "messaging",
                "sms", "weather", "tools") -> AppCategory.TOOLS
            else -> AppCategory.OTHER
        }
    }
}
