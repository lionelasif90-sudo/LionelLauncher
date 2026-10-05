package com.lionel.hub

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * Single checkpoint every app launch passes through (the launcher calls it from
 * startMainActivity). Applies focus mode + app lock and records usage.
 */
object LionelGate {
    private const val GRACE_MS = 60_000L
    private val unlockedAt = HashMap<String, Long>()

    /** @return true when the caller may launch the app now. */
    fun beforeLaunch(context: Context, packageName: String): Boolean {
        if (packageName == context.packageName) return true
        val store = LionelStore.get(context)

        if (store.isFocusOn && packageName in store.focusApps) {
            toast(context, "Focus mode is on")
            return false
        }

        if (packageName in store.lockedApps && !recentlyUnlocked(packageName)) {
            val intent = Intent(context, LionelLockActivity::class.java)
                .putExtra(LionelLockActivity.EXTRA_PACKAGE, packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return false
        }

        store.recordLaunch(packageName)
        return true
    }

    internal fun markUnlocked(packageName: String) {
        synchronized(unlockedAt) { unlockedAt[packageName] = System.currentTimeMillis() }
    }

    private fun recentlyUnlocked(packageName: String): Boolean = synchronized(unlockedAt) {
        val t = unlockedAt[packageName] ?: return false
        System.currentTimeMillis() - t < GRACE_MS
    }

    internal fun toast(context: Context, message: String) {
        val app = context.applicationContext
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
        }
    }
}
