package com.gymrest.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Safety net. The service normally rings the alarm itself, but we also ask Android's
 * AlarmManager to wake us at the exact moment rest ends, in case the phone's battery
 * manager put the service to sleep.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TimerService.send(context, TimerService.ACTION_FINISH)
    }
}
