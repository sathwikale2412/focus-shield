package com.example.focussheild

import android.graphics.drawable.Drawable

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable?,
    var isBlocked: Boolean = false,
    var blockedUntil: Long = 0L, // Timestamp in millis
    var blockKeywords: List<String> = emptyList()
)
