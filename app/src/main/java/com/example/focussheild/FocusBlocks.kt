package com.example.focussheild

import android.content.Context

/**
 * Removes timed blocks whose window has already passed.
 *
 * Enforcement stopped honouring an expired block the moment its timer ran out, but the package
 * stayed in `blocked_apps`, so the app list went on showing it as "Always blocked" forever.
 * Deleting the entry is the only way the list and the blocker can agree.
 */
fun pruneExpiredBlocks(context: Context) {
    val prefs = context.getSharedPreferences("focus_shield_prefs", Context.MODE_PRIVATE)
    val now = System.currentTimeMillis()
    val blocked = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()

    val expired = blocked.filter { pkg ->
        val until = prefs.getLong("blocked_until_$pkg", 0L)
        until > 0L && until <= now
    }
    if (expired.isEmpty()) return

    val editor = prefs.edit().putStringSet("blocked_apps", blocked - expired.toSet())
    expired.forEach { editor.remove("blocked_until_$it") }
    editor.apply()
}
