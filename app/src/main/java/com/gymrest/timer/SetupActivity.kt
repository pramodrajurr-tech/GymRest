package com.gymrest.timer

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** One screen that walks through every phone setting that affects the alarm. */
class SetupActivity : Activity() {

    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        container = findViewById(R.id.rows)
        findViewById<Button>(R.id.doneBtn).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        buildRows() // re-check statuses every time we come back from a settings screen
    }

    private fun buildRows() {
        container.removeAllViews()

        addRow(
            title = "Notifications",
            body = "Shows the countdown and the \"rest over\" alert.",
            status = notificationsOn(),
            actionLabel = "Allow notifications",
            action = { openNotificationSettings() }
        )

        addRow(
            title = "Battery: no restrictions",
            body = "Stops the phone from putting the timer to sleep while the screen is off.",
            status = batteryOk(),
            actionLabel = "Allow background running",
            action = { openBatterySettings() }
        )

        if (Build.VERSION.SDK_INT >= 34) {
            addRow(
                title = "Full-screen alerts",
                body = "Lets the alarm light up the lock screen. The sound plays even without this.",
                status = fullScreenOk(),
                actionLabel = "Open this setting",
                action = {
                    startSafely(
                        Intent(
                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
            )
        }

        addRow(
            title = "Vivo background settings",
            body = "Vivo's battery manager can close apps while the screen is off. Do all three:\n\n" +
                "1. Settings > Battery > Background power consumption management > Gym Rest > " +
                "Allow high background power consumption.\n\n" +
                "2. i Manager > App manager > Autostart (or Settings > Apps > Autostart) > " +
                "switch on Gym Rest.\n\n" +
                "3. Open Recent apps and lock Gym Rest (pull its card down, or tap the lock icon). " +
                "Never swipe it away.\n\n" +
                "Menu names differ a little between Funtouch OS and OriginOS.",
            status = null,
            actionLabel = "Open Gym Rest app settings",
            action = { openAppDetails() }
        )

        addRow(
            title = "Check the volume",
            body = "The alarm uses your phone's Alarm volume, not Media volume. Tap below, then lock " +
                "the phone or switch to another app. It rings in 10 seconds.",
            status = null,
            actionLabel = "Test alarm (10 sec)",
            action = {
                TimerService.send(this, TimerService.ACTION_START, 10_000L, test = true)
                finish()
            }
        )
    }

    private fun addRow(
        title: String,
        body: String,
        status: Boolean?,
        actionLabel: String,
        action: () -> Unit
    ) {
        val row = layoutInflater.inflate(R.layout.item_setup_row, container, false)
        row.findViewById<TextView>(R.id.rowTitle).text = title
        row.findViewById<TextView>(R.id.rowBody).text = body

        val statusView = row.findViewById<TextView>(R.id.rowStatus)
        when (status) {
            true -> {
                statusView.text = "Done"
                statusView.setTextColor(getColor(R.color.chalk_muted))
            }
            false -> {
                statusView.text = "Turn on"
                statusView.setTextColor(getColor(R.color.plate_yellow))
            }
            null -> statusView.text = ""
        }

        val button = row.findViewById<Button>(R.id.rowAction)
        button.text = actionLabel
        button.setOnClickListener { action() }

        container.addView(row)
    }

    // ------------------------------------------------------------------ status checks

    private fun notificationsOn(): Boolean =
        getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    private fun batteryOk(): Boolean =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun fullScreenOk(): Boolean =
        if (Build.VERSION.SDK_INT >= 34) {
            getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        } else {
            true
        }

    // ------------------------------------------------------------------ actions

    private fun openNotificationSettings() {
        // First try the normal permission pop-up; if it was already refused, open the settings page.
        if (Build.VERSION.SDK_INT >= 33 &&
            !Prefs.askedNotifications(this) &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Prefs.markAskedNotifications(this)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            return
        }
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        if (!startSafely(i)) openAppDetails()
    }

    private fun openBatterySettings() {
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName")
        )
        if (startSafely(direct)) return
        if (startSafely(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) return
        openAppDetails()
    }

    private fun openAppDetails() {
        startSafely(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        )
    }

    private fun startSafely(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }
}
