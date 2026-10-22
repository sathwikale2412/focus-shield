package com.example.focussheild

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class FocusAccessibilityService : AccessibilityService() {

    private val distractingKeywords = listOf(
        "shorts", "funny", "gaming", "comedy", "prank", "vlog", "entertainment", 
        "reaction", "meme", "challenge", "unboxing", "compilation", "gossip"
    )
    private val educationalKeywords = listOf(
        "tutorial", "course", "lecture", "science", "math", "history", "coding", 
        "how to", "physics", "development", "university", "education", "training",
        "documentation", "explained"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        android.util.Log.d("FocusShield", "Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // Log for debugging
        android.util.Log.d("FocusShield", "Event received from: ${event.packageName}")

        if (event.packageName == "com.google.android.youtube") {
            // Redmi Android 10 fix: Ensure we inspect even on scrolls or content changes
            inspectYouTube()
        }
    }

    private fun inspectYouTube() {
        val rootNode = rootInActiveWindow ?: return
        
        var isDistracting = false
        var isEducational = false
        
        // Redmi Android 10 fix: Broaden search to include more potential title locations
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        findAllTextNodes(rootNode, nodes)
        
        for (node in nodes) {
            val text = node.text?.toString()?.lowercase() ?: continue
            
            // Log detected text for troubleshooting
            // android.util.Log.d("FocusShield", "Text detected: $text")

            // 1. Check for high-distraction zones (Shorts/Reels)
            if (text.contains("shorts") || text.contains("reel") || text.contains("trending")) {
                isDistracting = true
                break
            }
            
            // 2. Educational priority
            if (educationalKeywords.any { text.contains(it) }) {
                isEducational = true
            }
            
            // 3. Distracting keywords
            if (distractingKeywords.any { text.contains(it) }) {
                isDistracting = true
            }
        }

        if (isDistracting && !isEducational) {
            android.util.Log.d("FocusShield", "YouTube Distraction Block Triggered!")
            blockApp("com.google.android.youtube")
        }
    }

    private fun findAllTextNodes(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        if (node.text != null) {
            list.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                findAllTextNodes(child, list)
            }
        }
    }

    private fun blockApp(packageName: String) {
        // Record Distraction in SharedPrefs
        val sharedPrefs = getSharedPreferences("focus_shield_prefs", Context.MODE_PRIVATE)
        val currentCount = sharedPrefs.getInt("distractions_count", 0)
        sharedPrefs.edit().putInt("distractions_count", currentCount + 1).apply()

        // Start the InterventionActivity to show the shame message and buzz
        // (Note: We use Intent because this service can start activities more easily than overlays)
        val intent = Intent(this, InterventionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("TARGET_PACKAGE", packageName)
            putExtra("REASON", "Distracting video detected! GET BACK TO STUDYING!")
        }
        startActivity(intent)
    }

    override fun onInterrupt() {}
}
