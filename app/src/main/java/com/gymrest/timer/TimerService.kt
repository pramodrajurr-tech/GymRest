package com.gymrest.timer

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Runs the rest countdown as a foreground service (so Android keeps it alive with the
 * screen off or another app open) and rings a looping alarm when time is up.
 */
class TimerService : Service() {

    companion object {
        const val ACTION_START = "com.gymrest.timer.START"
        const val ACTION_FINISH = "com.gymrest.timer.FINISH"
        const val ACTION_CANCEL = "com.gymrest.timer.CANCEL"
        const val ACTION_DISMISS = "com.gymrest.timer.DISMISS"
        const val EXTRA_DURATION = "duration"
        const val EXTRA_TEST = "test"

        private const val CH_TIMER = "rest_timer"
        private const val CH_ALARM = "rest_alarm"
        private const val NOTIF_STATUS = 42
        private const val NOTIF_ALARM = 43
        private const val MAX_RING_MS = 2 * 60 * 1000L // stop ringing by itself after 2 minutes

        fun send(context: Context, action: String, durationMs: Long = 0L, test: Boolean = false) {
            val i = Intent(context, TimerService::class.java)
                .setAction(action)
                .putExtra(EXTRA_DURATION, durationMs)
                .putExtra(EXTRA_TEST, test)
            context.startForegroundService(i)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val finishTask = Runnable { finishRest() }
    private val autoStopTask = Runnable { dismissAlarm() }

    private var isForeground = false
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var cpuLock: PowerManager.WakeLock? = null
    private var screenLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null

    private val alarmAttrs: AudioAttributes by lazy {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM) // plays on the ALARM volume, even if media is muted
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_TIMER, "Rest countdown", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows the running rest timer"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALARM, "Rest over alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Wakes the screen when rest time is over"
                setSound(null, null) // the service plays the sound itself
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val duration = intent?.getLongExtra(EXTRA_DURATION, 0L) ?: 0L
        val test = intent?.getBooleanExtra(EXTRA_TEST, false) ?: false

        when (action) {
            ACTION_START -> startRest(duration, test)
            ACTION_FINISH -> onBackupAlarm()
            ACTION_CANCEL -> cancelRest()
            ACTION_DISMISS -> dismissAlarm()
            else -> {
                goForegroundIfNeeded()
                shutDown()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopAlarmOutput()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ rest lifecycle

    private fun startRest(requestedMs: Long, test: Boolean) {
        stopAlarmOutput()
        handler.removeCallbacks(finishTask)
        handler.removeCallbacks(autoStopTask)

        val total = if (requestedMs > 0) requestedMs else Prefs.duration(this)
        val endAt = System.currentTimeMillis() + total

        if (!test) Prefs.addSet(this)
        Prefs.setRunning(this, endAt, total)

        goForeground(statusNotification("Resting", "Next set when this hits zero", endAt))
        scheduleBackup(endAt)
        handler.postDelayed(finishTask, total)
    }

    private fun finishRest() {
        if (Prefs.state(this) != Prefs.RUNNING) return
        handler.removeCallbacks(finishTask)
        cancelBackup()
        Prefs.setRinging(this)

        goForeground(statusNotification("Rest over", "Tap to open the timer", null))
        startAlarmOutput()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ALARM, alarmNotification())

        handler.postDelayed(autoStopTask, MAX_RING_MS)
    }

    /** Fired by AlarmManager. Normally the handler already did the job. */
    private fun onBackupAlarm() {
        if (Prefs.state(this) == Prefs.RUNNING) {
            goForeground(statusNotification("Rest over", "Tap to open the timer", null))
            finishRest()
        } else {
            goForegroundIfNeeded()
            if (Prefs.state(this) == Prefs.IDLE) shutDown()
        }
    }

    private fun cancelRest() {
        goForegroundIfNeeded()
        handler.removeCallbacks(finishTask)
        handler.removeCallbacks(autoStopTask)
        cancelBackup()
        stopAlarmOutput()
        Prefs.setIdle(this)
        shutDown()
    }

    private fun dismissAlarm() {
        goForegroundIfNeeded()
        handler.removeCallbacks(finishTask)
        handler.removeCallbacks(autoStopTask)
        cancelBackup()
        stopAlarmOutput()
        Prefs.setIdle(this)
        shutDown()
    }

    private fun shutDown() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        isForeground = false
        stopSelf()
    }

    // ------------------------------------------------------------------ alarm output

    @Suppress("DEPRECATION")
    private fun startAlarmOutput() {
        stopAlarmOutput()

        val pm = getSystemService(PowerManager::class.java)

        // Keep the CPU awake while ringing
        cpuLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "gymrest:ring").apply {
            setReferenceCounted(false)
            acquire(MAX_RING_MS + 10_000L)
        }
        // Light up the screen
        screenLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "gymrest:screen"
        ).apply {
            setReferenceCounted(false)
            acquire(15_000L)
        }

        // Pause/duck other audio (music) while the alarm rings
        try {
            val am = getSystemService(AudioManager::class.java)
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(alarmAttrs)
                .build()
            am.requestAudioFocus(req)
            focusRequest = req
        } catch (e: Exception) {
            // not critical
        }

        startSound()
        startVibration()
    }

    private fun startSound() {
        // 1) our own sharp beep   2) phone's alarm tone   3) notification tone   4) ringtone
        val ok = tryPlay { mp ->
            resources.openRawResourceFd(R.raw.rest_over).use { afd ->
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
        }
        if (ok) return

        val types = intArrayOf(
            RingtoneManager.TYPE_ALARM,
            RingtoneManager.TYPE_NOTIFICATION,
            RingtoneManager.TYPE_RINGTONE
        )
        for (type in types) {
            val uri = RingtoneManager.getDefaultUri(type) ?: continue
            if (tryPlay { mp -> mp.setDataSource(this, uri) }) return
        }
    }

    private fun tryPlay(setup: (MediaPlayer) -> Unit): Boolean {
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(alarmAttrs)
            setup(mp)
            mp.isLooping = true
            mp.prepare()
            mp.start()
            player = mp
            true
        } catch (e: Exception) {
            try {
                mp.release()
            } catch (_: Exception) {
            }
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        try {
            val v = getSystemService(Vibrator::class.java) ?: return
            if (!v.hasVibrator()) return
            vibrator = v
            val pattern = longArrayOf(0, 500, 300, 500, 300, 500, 1500)
            v.vibrate(VibrationEffect.createWaveform(pattern, 0), alarmAttrs)
        } catch (e: Exception) {
            // not critical
        }
    }

    private fun stopAlarmOutput() {
        player?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            try {
                it.release()
            } catch (_: Exception) {
            }
        }
        player = null

        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }
        vibrator = null

        try {
            focusRequest?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        } catch (_: Exception) {
        }
        focusRequest = null

        try {
            if (cpuLock?.isHeld == true) cpuLock?.release()
        } catch (_: Exception) {
        }
        cpuLock = null
        try {
            if (screenLock?.isHeld == true) screenLock?.release()
        } catch (_: Exception) {
        }
        screenLock = null

        getSystemService(NotificationManager::class.java).cancel(NOTIF_ALARM)
    }

    // ------------------------------------------------------------------ AlarmManager backup

    private fun backupIntent(): PendingIntent = PendingIntent.getBroadcast(
        this, 100,
        Intent(this, AlarmReceiver::class.java).setAction(ACTION_FINISH),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun scheduleBackup(endAt: Long) {
        try {
            val am = getSystemService(AlarmManager::class.java)
            val show = PendingIntent.getActivity(
                this, 101, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            // setAlarmClock is treated like a real alarm clock: it fires on time even in Doze
            am.setAlarmClock(AlarmManager.AlarmClockInfo(endAt, show), backupIntent())
        } catch (e: SecurityException) {
            // exact alarms not allowed; the in-service timer still works
        }
    }

    private fun cancelBackup() {
        try {
            getSystemService(AlarmManager::class.java).cancel(backupIntent())
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------------ notifications

    private fun goForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_STATUS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_STATUS, n)
        }
        isForeground = true
    }

    /** Android requires startForeground() after startForegroundService(), even if we stop right away. */
    private fun goForegroundIfNeeded() {
        if (!isForeground) goForeground(statusNotification("Gym Rest", "One moment…", null))
    }

    private fun immutableUpdate() = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    private fun openAppIntent(requestCode: Int): PendingIntent = PendingIntent.getActivity(
        this, requestCode,
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        immutableUpdate()
    )

    private fun serviceAction(requestCode: Int, action: String, label: String): Notification.Action {
        val pi = PendingIntent.getForegroundService(
            this, requestCode,
            Intent(this, TimerService::class.java).setAction(action),
            immutableUpdate()
        )
        return Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_stat_timer), label, pi
        ).build()
    }

    private fun statusNotification(title: String, text: String, countdownEnd: Long?): Notification {
        val b = Notification.Builder(this, CH_TIMER)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent(0))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        if (countdownEnd != null) {
            b.setShowWhen(true)
                .setWhen(countdownEnd)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .addAction(serviceAction(1, ACTION_CANCEL, "Skip rest"))
        } else {
            b.setShowWhen(false)
                .addAction(serviceAction(2, ACTION_DISMISS, "Stop alarm"))
        }

        if (Build.VERSION.SDK_INT >= 31) {
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return b.build()
    }

    /** High-priority notification with a full-screen intent: this is what lights up the lock screen. */
    private fun alarmNotification(): Notification {
        val open = openAppIntent(3)
        return Notification.Builder(this, CH_ALARM)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Rest over")
            .setContentText("Time for your next set")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .addAction(serviceAction(4, ACTION_DISMISS, "Stop alarm"))
            .build()
    }
}
