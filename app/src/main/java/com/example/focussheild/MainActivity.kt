package com.example.focussheild

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.focussheild.ui.theme.FocusSheildTheme

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            viewModel = viewModel()
            FocusSheildTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FocusShieldApp(viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.checkPermission()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusShieldApp(viewModel: MainViewModel = viewModel()) {
    val apps by viewModel.apps.collectAsState()
    val isPermissionGranted by viewModel.isPermissionGranted.collectAsState()
    val isOverlayPermissionGranted by viewModel.isOverlayPermissionGranted.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Focus Shield", style = MaterialTheme.typography.titleLarge)
                        val totalTime by viewModel.totalScreenTime.collectAsState()
                        Text("Today: $totalTime", style = MaterialTheme.typography.bodySmall)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }) {
                        Text("AI Filter (YouTube)", style = MaterialTheme.typography.labelSmall)
                    }
                    if (!isPermissionGranted) {
                        TextButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }) {
                            Text("Grant Permission", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Switch(
                        checked = isServiceRunning,
                        enabled = isPermissionGranted && isOverlayPermissionGranted,
                        onCheckedChange = {
                            viewModel.toggleServiceState(it)
                            val intent = Intent(context, FocusService::class.java)
                            if (it) {
                                context.startForegroundService(intent)
                            } else {
                                context.stopService(intent)
                            }
                        }
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Shield, contentDescription = null) },
                    label = { Text("Apps") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text("Profile") }
                )
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (selectedTab == 0) {
                AppListScreen(apps, isPermissionGranted, isOverlayPermissionGranted, viewModel)
            } else {
                ProfileScreen(viewModel)
            }
        }
    }
}

@Composable
fun AppListScreen(apps: List<AppInfo>, isPermissionGranted: Boolean, isOverlayPermissionGranted: Boolean, viewModel: MainViewModel) {
    val context = LocalContext.current
    Column {
        if (!isPermissionGranted) {
            PermissionBanner(
                text = "Usage Access permission is required.",
                onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            )
        }
        if (!isOverlayPermissionGranted) {
            PermissionBanner(
                text = "Display over other apps permission is required.",
                onClick = { 
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    intent.data = android.net.Uri.parse("package:${context.packageName}")
                    context.startActivity(intent)
                }
            )
        }
        
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "💡 Tip: Unblock YouTube here to use the 'AI Filter' for specific videos!",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.labelSmall
            )
        }

        LazyColumn {
            items(apps) { app ->
                AppItem(app = app, viewModel = viewModel, onToggle = { viewModel.toggleAppBlock(app.packageName) })
            }
        }
    }
}

@Composable
fun PermissionBanner(text: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
fun ProfileScreen(viewModel: MainViewModel) {
    val totalTime by viewModel.totalScreenTime.collectAsState()
    val distractions by viewModel.distractionsDetected.collectAsState()
    val focusScore by viewModel.focusScore.collectAsState()
    val status by viewModel.productivityStatus.collectAsState()
    val weeklyData by viewModel.weeklyReportData.collectAsState()
    
    val userName = viewModel.getUserName()
    val userAim = viewModel.getUserAim()
    val userExam = viewModel.getUserExam()
    val institute = viewModel.getUserInstitute()
    val timePeriod = viewModel.getUserTimePeriod()
    
    var showReportDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // User Profile Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        modifier = Modifier.size(80.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(40.dp), tint = Color.White)
                        }
                    }
                    Text(text = userName.ifEmpty { "User Name" }, style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "AIM: ${userAim.ifEmpty { "Not Set" }}",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    
                    if (userExam.isNotEmpty()) Text("Preparing for: $userExam", style = MaterialTheme.typography.bodyMedium)
                    if (institute.isNotEmpty()) Text("At: $institute", style = MaterialTheme.typography.bodySmall)
                    if (timePeriod.isNotEmpty()) Text("Target: $timePeriod", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        item {
            // Productivity Score Output
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Current Focus Score", color = MaterialTheme.colorScheme.onPrimary)
                    Text(
                        text = "$focusScore%",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text("Mode: $status", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                }
            }
        }

        item {
            // Detailed Monitoring Output
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AnalyticsCard("Screen Time", totalTime, Modifier.weight(1f))
                AnalyticsCard("Distractions", "$distractions", Modifier.weight(1f))
            }
        }

        item {
            // Configuration & Report Section
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Productivity Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Button(
                    onClick = { showReportDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Generate Usage Report")
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showSettingsDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Text("Focus Mode Settings")
                }
            }
        }
        
        item {
            Text(
                "Focus Shield v1.2 | Data updated just now",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }

    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Weekly Usage Report") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Total usage over the last 7 days:", style = MaterialTheme.typography.bodyMedium)
                    weeklyData.forEach { (day, millis) ->
                        val hours = millis / (1000 * 60 * 60)
                        val minutes = (millis / (1000 * 60)) % 60
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(day, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Text("${hours}h ${minutes}m")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReportDialog = false }) { Text("Close") }
            }
        )
    }

    if (showSettingsDialog) {
        var goalHours by remember { mutableStateOf(viewModel.getFocusGoal().toString()) }
        var userName by remember { mutableStateOf(viewModel.getUserName()) }
        var userAim by remember { mutableStateOf(viewModel.getUserAim()) }
        var phone by remember { mutableStateOf(viewModel.getUserPhone()) }
        var institute by remember { mutableStateOf(viewModel.getUserInstitute()) }
        var timePeriod by remember { mutableStateOf(viewModel.getUserTimePeriod()) }
        var exam by remember { mutableStateOf(viewModel.getUserExam()) }
        
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Focus Mode & Profile") },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        OutlinedTextField(value = userName, onValueChange = { userName = it }, label = { Text("Your Name") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = userAim, onValueChange = { userAim = it }, label = { Text("Your Aim (e.g. IAS Officer)") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = exam, onValueChange = { exam = it }, label = { Text("Exam you are writing") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = institute, onValueChange = { institute = it }, label = { Text("Institute/Society Name") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = timePeriod, onValueChange = { timePeriod = it }, label = { Text("Time to achieve (e.g. 12 months)") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(16.dp))
                        Text("Daily Focus Goal (Hours)")
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = goalHours, onValueChange = { goalHours = it.filter { char -> char.isDigit() } }, label = { Text("Target Hours") }, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.setUserProfile(userName, userAim, phone, institute, timePeriod, exam)
                    viewModel.setFocusGoal(goalHours.toIntOrNull() ?: 4)
                    showSettingsDialog = false
                }) { Text("Save Profile") }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun AnalyticsCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun AppItem(app: AppInfo, viewModel: MainViewModel = viewModel(), onToggle: () -> Unit) {
    var showTimeDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            app.icon?.let {
                Image(
                    bitmap = it.toBitmap().asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = app.name, style = MaterialTheme.typography.bodyLarge)
                if (app.isBlocked) {
                    val statusText = if (app.blockedUntil > System.currentTimeMillis()) {
                        val remaining = (app.blockedUntil - System.currentTimeMillis()) / (60 * 1000)
                        "Blocked for ${remaining}m"
                    } else "Always blocked"
                    
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            IconButton(onClick = { showTimeDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = "Timer",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Switch(
                checked = app.isBlocked,
                onCheckedChange = { onToggle() }
            )
        }
    }

    if (showTimeDialog) {
        AlertDialog(
            onDismissRequest = { showTimeDialog = false },
            title = { Text("Block Duration") },
            text = { 
                Column {
                    Text("How long do you want to block ${app.name}?")
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf(15, 30, 60, 120).forEach { mins ->
                            Button(
                                onClick = { 
                                    viewModel.setBlockDuration(app.packageName, mins)
                                    showTimeDialog = false 
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                val label = if (mins >= 60) "${mins/60}h" else "${mins}m"
                                Text(label)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTimeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
