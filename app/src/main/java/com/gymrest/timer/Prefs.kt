package com.gymrest.timer

import android.content.Context

/** Tiny shared state so the screen and the background service always agree. */
object Prefs {
    const val IDLE = 0
    const val RUNNING = 1
    const val RINGING = 2

    const val MIN_REST_MS = 15_000L
    const val MAX_REST_MS = 15 * 60_000L
    const val DEFAULT_REST_MS = 180_000L // 3 minutes

    private fun sp(c: Context) = c.getSharedPreferences("gym_rest", Context.MODE_PRIVATE)

    fun duration(c: Context): Long = sp(c).getLong("duration", DEFAULT_REST_MS)

    fun setDuration(c: Context, ms: Long) {
        sp(c).edit().putLong("duration", ms.coerceIn(MIN_REST_MS, MAX_REST_MS)).apply()
    }

    fun state(c: Context): Int = sp(c).getInt("state", IDLE)
    fun endAt(c: Context): Long = sp(c).getLong("endAt", 0L)
    fun totalMs(c: Context): Long = sp(c).getLong("total", DEFAULT_REST_MS)

    fun setRunning(c: Context, endAt: Long, totalMs: Long) {
        sp(c).edit().putInt("state", RUNNING).putLong("endAt", endAt).putLong("total", totalMs).apply()
    }

    fun setRinging(c: Context) {
        sp(c).edit().putInt("state", RINGING).apply()
    }

    fun setIdle(c: Context) {
        sp(c).edit().putInt("state", IDLE).putLong("endAt", 0L).apply()
    }

    fun sets(c: Context): Int = sp(c).getInt("sets", 0)
    fun addSet(c: Context) {
        sp(c).edit().putInt("sets", sets(c) + 1).apply()
    }
    fun resetSets(c: Context) {
        sp(c).edit().putInt("sets", 0).apply()
    }

    fun setupSeen(c: Context): Boolean = sp(c).getBoolean("setupSeen", false)
    fun markSetupSeen(c: Context) {
        sp(c).edit().putBoolean("setupSeen", true).apply()
    }

    fun askedNotifications(c: Context): Boolean = sp(c).getBoolean("askedNotif", false)
    fun markAskedNotifications(c: Context) {
        sp(c).edit().putBoolean("askedNotif", true).apply()
    }
}
