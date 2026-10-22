package com.example.focussheild

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.os.Vibrator
import android.os.VibrationEffect
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focussheild.ui.theme.FocusSheildTheme
import kotlinx.coroutines.delay

class InterventionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val targetPackage = intent.getStringExtra("TARGET_PACKAGE") ?: ""
        val reason = intent.getStringExtra("REASON") ?: "Time to focus!"

        // Buzz Sound
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(500)
        }

        setContent {
            FocusSheildTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    InterventionScreen(
                        packageName = targetPackage,
                        reason = reason,
                        onContinue = { finish() },
                        onExit = {
                            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(homeIntent)
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun InterventionScreen(packageName: String, reason: String, onContinue: () -> Unit, onExit: () -> Unit) {
    var timeLeft by remember { mutableIntStateOf(5) }
    val progressAnim = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        while (timeLeft > 0) {
            progressAnim.animateTo(
                targetValue = (timeLeft - 1) / 5f,
                animationSpec = tween(1000, easing = LinearEasing)
            )
            timeLeft--
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "SHAME ON YOU!",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = reason,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
            val color = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * progressAnim.value,
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx())
                )
            }
            Text(text = timeLeft.toString(), fontSize = 48.sp)
        }

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Do you really need to open this app right now?",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("I'll do something else")
        }

        if (timeLeft == 0) {
            TextButton(
                onClick = onContinue,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Continue to App", color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
