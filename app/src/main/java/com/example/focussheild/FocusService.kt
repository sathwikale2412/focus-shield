package com.example.focussheild

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.os.Vibrator
import android.os.VibrationEffect
import androidx.core.app.NotificationCompat

class FocusService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val checkInterval = 300L 
    private var windowManager: WindowManager? = null
    private var overlayView: FrameLayout? = null

    private val checkTask = object : Runnable {
        override fun run() {
            checkForegroundApp()
            handler.postDelayed(this, checkInterval)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "focus_shield_channel")
            .setContentTitle("Focus Shield is Guarding")
            .setContentText("Monitoring for distractions...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
        startForeground(1, notification)
        
        handler.post(checkTask)
        return START_STICKY
    }

    private fun checkForegroundApp() {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        
        val usageEvents = usageStatsManager.queryEvents(time - 2000, time)
        var currentForegroundApp: String? = null
        val event = UsageEvents.Event()
        
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                currentForegroundApp = event.packageName
            }
        }

        if (currentForegroundApp == null) {
            val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 5, time)
            currentForegroundApp = stats?.maxByOrNull { it.lastTimeUsed }?.packageName
        }
        
        if (currentForegroundApp != null && currentForegroundApp != packageName) {
            val sharedPrefs = getSharedPreferences("focus_shield_prefs", Context.MODE_PRIVATE)
            val blockedApps = sharedPrefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()
            
            if (blockedApps.contains(currentForegroundApp)) {
                val blockedUntil = sharedPrefs.getLong("blocked_until_$currentForegroundApp", 0L)
                if (blockedUntil == 0L || blockedUntil > System.currentTimeMillis()) {
                    showOverlay(currentForegroundApp, isShame = true)
                    forceGoHome()
                }
            } else {
                // Potential Motivational Message Trigger
                val lastMotivationTime = sharedPrefs.getLong("last_motivation_time", 0L)
                if (System.currentTimeMillis() - lastMotivationTime > 3600000) { 
                    showOverlay(currentForegroundApp, isShame = false)
                    sharedPrefs.edit().putLong("last_motivation_time", System.currentTimeMillis()).apply()
                }
            }
        }
    }

    private fun forceGoHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
    }

    private fun showOverlay(packageName: String, isShame: Boolean) {
        if (overlayView != null) return

        handler.post {
            try {
                val sharedPrefs = getSharedPreferences("focus_shield_prefs", Context.MODE_PRIVATE)
                
                if (isShame) {
                    val currentCount = sharedPrefs.getInt("distractions_count", 0)
                    sharedPrefs.edit().putInt("distractions_count", currentCount + 1).apply()
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or 
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or 
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                }

                overlayView = FrameLayout(this).apply {
                    setBackgroundColor(if (isShame) Color.parseColor("#EE0D47A1") else Color.parseColor("#EE388E3C"))
                    
                    val name = sharedPrefs.getString("user_name", "") ?: "Student"
                    val aim = sharedPrefs.getString("user_aim", "") ?: "Goal"
                    val exam = sharedPrefs.getString("user_exam", "") ?: "Success"
                    
                    val message = if (isShame) {
                        "SHAME ON YOU, $name!\n\nIs this how a future $aim spends their time?\nYour $exam exam is waiting!\n\nGET BACK TO WORK!"
                    } else {
                        "Excellent work, $name!\n\nYou are on your way to becoming a $aim.\n\nKeep focusing for that $exam!"
                    }

                    val container = android.widget.LinearLayout(context).apply {
                        orientation = android.widget.LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        setPadding(60, 60, 60, 60)
                    }

                    val textView = TextView(context).apply {
                        text = message
                        setTextColor(Color.WHITE)
                        textSize = 26f
                        gravity = Gravity.CENTER
                    }
                    
                    val actionButton = android.widget.Button(context).apply {
                        text = if (isShame) "I promise to focus now" else "Thank you, I'll keep it up"
                        setBackgroundColor(Color.WHITE)
                        setTextColor(if (isShame) Color.parseColor("#0D47A1") else Color.parseColor("#388E3C"))
                        setOnClickListener {
                            removeOverlay()
                            forceGoHome()
                        }
                    }
                    
                    container.addView(textView)
                    container.addView(actionButton, android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 60 })
                    
                    addView(container, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))
                }
                
                windowManager?.addView(overlayView, params)

                // Buzz Sound
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                    vibratorManager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                
            } catch (e: Exception) {
                android.util.Log.e("FocusShield", "Shield failed", e)
            }
        }
    }

    private fun removeOverlay() {
        if (overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {}
            overlayView = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "focus_shield_channel",
                "Focus Shield Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(checkTask)
        removeOverlay()
        super.onDestroy()
    }
}
