package com.example.focussheild

import android.app.AppOpsManager
import android.app.Application
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("focus_shield_prefs", Context.MODE_PRIVATE)
    
    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "distractions_count") {
            loadAnalytics()
        }
    }

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    private val _isPermissionGranted = MutableStateFlow(false)
    val isPermissionGranted: StateFlow<Boolean> = _isPermissionGranted

    private val _isOverlayPermissionGranted = MutableStateFlow(false)
    val isOverlayPermissionGranted: StateFlow<Boolean> = _isOverlayPermissionGranted

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning

    private val _totalScreenTime = MutableStateFlow("0h 0m")
    val totalScreenTime: StateFlow<String> = _totalScreenTime

    private val _distractionsDetected = MutableStateFlow(0)
    val distractionsDetected: StateFlow<Int> = _distractionsDetected

    private val _focusScore = MutableStateFlow(100)
    val focusScore: StateFlow<Int> = _focusScore

    private val _productivityStatus = MutableStateFlow("Productive")
    val productivityStatus: StateFlow<String> = _productivityStatus

    private val _weeklyReportData = MutableStateFlow<Map<String, Long>>(emptyMap())
    val weeklyReportData: StateFlow<Map<String, Long>> = _weeklyReportData

    init {
        sharedPrefs.registerOnSharedPreferenceChangeListener(prefListener)
        loadApps()
        checkPermission()
        calculateTotalUsage()
        loadAnalytics()
        generateWeeklyReport()
        refreshServiceState()
        watchServiceHeartbeat()
    }

    override fun onCleared() {
        super.onCleared()
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    private fun loadAnalytics() {
        _distractionsDetected.value = sharedPrefs.getInt("distractions_count", 0)
        updateProductivityScore()
    }

    private fun generateWeeklyReport() {
        viewModelScope.launch {
            val usageStatsManager = getApplication<Application>().getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 1000L * 60 * 60 * 24 * 7 // Last 7 days
            val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
            
            val report = mutableMapOf<String, Long>()
            stats?.forEach {
                val day = android.text.format.DateFormat.format("EEE", it.firstTimeStamp).toString()
                report[day] = (report[day] ?: 0L) + it.totalTimeInForeground
            }
            _weeklyReportData.value = report
        }
    }

    fun setFocusGoal(hours: Int) {
        sharedPrefs.edit().putInt("focus_goal_hours", hours).apply()
        updateProductivityScore()
    }

    fun getFocusGoal(): Int = sharedPrefs.getInt("focus_goal_hours", 4)

    fun setUserProfile(name: String, aim: String, phone: String, institute: String, timePeriod: String, exam: String) {
        sharedPrefs.edit()
            .putString("user_name", name)
            .putString("user_aim", aim)
            .putString("user_phone", phone)
            .putString("user_institute", institute)
            .putString("user_time_period", timePeriod)
            .putString("user_exam", exam)
            .apply()
    }

    fun getUserPhone(): String = sharedPrefs.getString("user_phone", "") ?: ""
    fun getUserInstitute(): String = sharedPrefs.getString("user_institute", "") ?: ""
    fun getUserTimePeriod(): String = sharedPrefs.getString("user_time_period", "") ?: ""
    fun getUserExam(): String = sharedPrefs.getString("user_exam", "") ?: ""

    fun getUserName(): String = sharedPrefs.getString("user_name", "") ?: ""
    fun getUserAim(): String = sharedPrefs.getString("user_aim", "") ?: ""

    private fun updateProductivityScore() {
        val distractions = _distractionsDetected.value
        val score = (100 - (distractions * 5)).coerceAtLeast(0)
        _focusScore.value = score
        _productivityStatus.value = when {
            score > 80 -> "High Focus"
            score > 50 -> "Balanced"
            else -> "Needs Improvement"
        }
    }

    fun recordDistraction() {
        val newCount = _distractionsDetected.value + 1
        _distractionsDetected.value = newCount
        sharedPrefs.edit().putInt("distractions_count", newCount).apply()
        updateProductivityScore()
    }

    private fun calculateTotalUsage() {
        viewModelScope.launch {
            while(true) {
                val usageStatsManager = getApplication<Application>().getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val calendar = java.util.Calendar.getInstance()
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                
                val startTime = calendar.timeInMillis
                val endTime = System.currentTimeMillis()
                
                // Query stats from the start of the current day for accuracy
                val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
                
                var totalMillis = 0L
                stats?.forEach {
                    if (it.totalTimeInForeground > 0) {
                        // On some devices we need to be careful about which interval we sum
                        // Filtering by lastTimeUsed being from today
                        if (it.lastTimeUsed >= startTime) {
                            totalMillis += it.totalTimeInForeground
                        }
                    }
                }
                
                val hours = totalMillis / (1000 * 60 * 60)
                val minutes = (totalMillis / (1000 * 60)) % 60
                _totalScreenTime.value = "${hours}h ${minutes}m"
                
                kotlinx.coroutines.delay(5000) // Update every 5 seconds for "real-time" feel
            }
        }
    }

    fun checkPermission() {
        val appOps = getApplication<Application>().getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            getApplication<Application>().packageName
        )
        _isPermissionGranted.value = mode == AppOpsManager.MODE_ALLOWED

        // Overlay Permission
        _isOverlayPermissionGranted.value = android.provider.Settings.canDrawOverlays(getApplication())
    }

    fun toggleServiceState(isRunning: Boolean) {
        _isServiceRunning.value = isRunning
        if (!isRunning) sharedPrefs.edit().putLong("service_heartbeat", 0L).apply()
    }

    // The switch used to read back a persisted boolean, so it claimed the blocker was running
    // after a reboot or a system kill. FocusService stamps service_heartbeat while its polling
    // loop is alive, and that stamp is the only honest answer.
    private fun refreshServiceState() {
        val lastBeat = sharedPrefs.getLong("service_heartbeat", 0L)
        _isServiceRunning.value = System.currentTimeMillis() - lastBeat < 5000
    }

    private fun watchServiceHeartbeat() {
        viewModelScope.launch {
            while (true) {
                refreshServiceState()
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    private fun loadApps() {
        viewModelScope.launch {
            pruneExpiredBlocks(getApplication<Application>())
            val pm = getApplication<Application>().packageManager
            val intent = Intent(Intent.ACTION_MAIN, null)
            intent.addCategory(Intent.CATEGORY_LAUNCHER)
            
            // Get all apps that appear in the app drawer
            val launchableApps = pm.queryIntentActivities(intent, 0)
            val blockedApps = sharedPrefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()

            val appList = launchableApps.map { resolveInfo ->
                val appInfo = resolveInfo.activityInfo.applicationInfo
                AppInfo(
                    name = resolveInfo.loadLabel(pm).toString(),
                    packageName = appInfo.packageName,
                    icon = resolveInfo.loadIcon(pm),
                    isBlocked = blockedApps.contains(appInfo.packageName),
                    blockedUntil = sharedPrefs.getLong("blocked_until_${appInfo.packageName}", 0L)
                )
            }.distinctBy { it.packageName }.sortedBy { it.name }

            _apps.value = appList
        }
    }

    fun toggleAppBlock(packageName: String) {
        val currentBlocked = sharedPrefs.getStringSet("blocked_apps", emptySet())?.toMutableSet() ?: mutableSetOf()
        
        if (currentBlocked.contains(packageName)) {
            currentBlocked.remove(packageName)
            sharedPrefs.edit().remove("blocked_until_$packageName").apply()
        } else {
            currentBlocked.add(packageName)
        }
        
        sharedPrefs.edit().putStringSet("blocked_apps", currentBlocked).apply()

        _apps.value = _apps.value.map {
            if (it.packageName == packageName) {
                it.copy(isBlocked = !it.isBlocked, blockedUntil = 0L)
            } else {
                it
            }
        }
    }

    fun setBlockDuration(packageName: String, minutes: Int) {
        val until = System.currentTimeMillis() + (minutes * 60 * 1000L)
        sharedPrefs.edit().putLong("blocked_until_$packageName", until).apply()
        
        // Also ensure it's in the blocked set
        val currentBlocked = sharedPrefs.getStringSet("blocked_apps", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (!currentBlocked.contains(packageName)) {
            currentBlocked.add(packageName)
            sharedPrefs.edit().putStringSet("blocked_apps", currentBlocked).apply()
        }

        _apps.value = _apps.value.map {
            if (it.packageName == packageName) {
                it.copy(isBlocked = true, blockedUntil = until)
            } else {
                it
            }
        }
    }
}
