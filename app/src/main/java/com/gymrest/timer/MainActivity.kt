package com.gymrest.timer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var root: View
    private lateinit var ring: RingView
    private lateinit var setsText: TextView
    private lateinit var stepper: View
    private lateinit var primary: Button

    private val handler = Handler(Looper.getMainLooper())
    private var shownState = -1
    private var nudgedFinish = false

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        ring = findViewById(R.id.ring)
        setsText = findViewById(R.id.setsText)
        stepper = findViewById(R.id.stepper)
        primary = findViewById(R.id.primaryBtn)

        findViewById<Button>(R.id.minusBtn).setOnClickListener {
            Prefs.setDuration(this, Prefs.duration(this) - 15_000L)
            refresh()
        }
        findViewById<Button>(R.id.plusBtn).setOnClickListener {
            Prefs.setDuration(this, Prefs.duration(this) + 15_000L)
            refresh()
        }
        findViewById<Button>(R.id.resetBtn).setOnClickListener {
            Prefs.resetSets(this)
            refresh()
        }
        findViewById<Button>(R.id.testBtn).setOnClickListener {
            // 10-second test so you can lock the phone or switch apps and check it still rings
            TimerService.send(this, TimerService.ACTION_START, 10_000L, test = true)
        }
        findViewById<Button>(R.id.setupBtn).setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }

        primary.setOnClickListener {
            when (Prefs.state(this)) {
                Prefs.IDLE -> {
                    askForNotificationsIfNeeded()
                    TimerService.send(this, TimerService.ACTION_START, Prefs.duration(this))
                }
                Prefs.RUNNING -> TimerService.send(this, TimerService.ACTION_CANCEL)
                else -> TimerService.send(this, TimerService.ACTION_DISMISS)
            }
        }

        // First launch: walk through the phone settings that let the alarm ring while locked
        if (!Prefs.setupSeen(this)) {
            Prefs.markSetupSeen(this)
            startActivity(Intent(this, SetupActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        shownState = -1
        nudgedFinish = false
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    private fun askForNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun refresh() {
        val now = System.currentTimeMillis()
        var state = Prefs.state(this)

        // Stale "running" state (e.g. phone was restarted mid-rest): reset it
        if (state == Prefs.RUNNING && now > Prefs.endAt(this) + 60_000L) {
            Prefs.setIdle(this)
            state = Prefs.IDLE
        }

        val chalk = getColor(R.color.chalk)
        val yellow = getColor(R.color.plate_yellow)

        when (state) {
            Prefs.RUNNING -> {
                val end = Prefs.endAt(this)
                val total = Prefs.totalMs(this).coerceAtLeast(1L)
                val remaining = (end - now).coerceAtLeast(0L)
                ring.show(remaining.toFloat() / total, fmt(remaining), "resting", yellow, chalk)

                // If the timer is over but the service hasn't rung yet, give it a nudge
                if (remaining == 0L && now > end + 3_000L && !nudgedFinish) {
                    nudgedFinish = true
                    TimerService.send(this, TimerService.ACTION_FINISH)
                }
            }
            Prefs.RINGING -> ring.show(1f, "GO", "rest over", chalk, chalk)
            else -> ring.show(1f, fmt(Prefs.duration(this)), "rest length", yellow, chalk)
        }

        val sets = Prefs.sets(this)
        setsText.text = when (sets) {
            0 -> "No sets yet"
            1 -> "1 set done"
            else -> "$sets sets done"
        }

        if (state == Prefs.IDLE) {
            val label = "Start " + fmt(Prefs.duration(this)) + " rest"
            if (primary.text.toString() != label) primary.text = label
        }

        if (state != shownState) {
            shownState = state
            applyStateStyle(state)
        }
    }

    /** Colours and button wording change only when the state changes. */
    private fun applyStateStyle(state: Int) {
        val bg = if (state == Prefs.RINGING) R.color.plate_red else R.color.plate_blue
        val bgColor = getColor(bg)
        root.setBackgroundColor(bgColor)
        window.statusBarColor = bgColor
        window.navigationBarColor = bgColor

        stepper.visibility = if (state == Prefs.IDLE) View.VISIBLE else View.INVISIBLE

        when (state) {
            Prefs.RUNNING -> {
                primary.setBackgroundResource(R.drawable.btn_outline)
                primary.setTextColor(getColor(R.color.chalk))
                primary.text = "Skip rest"
            }
            Prefs.RINGING -> {
                primary.setBackgroundResource(R.drawable.btn_alarm)
                primary.setTextColor(getColor(R.color.plate_red))
                primary.text = "Stop alarm"
            }
            else -> {
                primary.setBackgroundResource(R.drawable.btn_primary)
                primary.setTextColor(getColor(R.color.ink))
                // text is kept up to date in refresh()
            }
        }
    }

    private fun fmt(ms: Long): String {
        val totalSec = (ms + 999L) / 1000L
        return String.format(Locale.US, "%d:%02d", totalSec / 60, totalSec % 60)
    }
}
