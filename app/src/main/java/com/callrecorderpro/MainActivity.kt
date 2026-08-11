package com.callrecorderpro

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.callrecorderpro.data.RecordingItem
import com.callrecorderpro.recorder.WhatsAppRecorderService
import com.callrecorderpro.ui.screens.HomeScreen
import com.callrecorderpro.ui.screens.PlayerScreen
import com.callrecorderpro.ui.theme.RecordProTheme

class MainActivity : ComponentActivity() {

    private val requiredPermissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_AUDIO)
            add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request MediaProjection permission for WhatsApp call audio capture
        requestMediaProjectionPermission()

        setContent {
            RecordProTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                    RecordProApp()
                }
            }
        }
    }

    private fun requestMediaProjectionPermission() {
        val projManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = projManager.createScreenCaptureIntent()
        // Will be launched by the Compose launcher below
    }
}

@Composable
fun RecordProApp() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    val activity = androidx.compose.ui.platform.LocalContext.current as Activity

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions handled gracefully */ }

    // MediaProjection launcher (for WhatsApp audio capture)
    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            WhatsAppRecorderService.mediaProjectionIntent = result.data
            WhatsAppRecorderService.mediaProjectionResult = result.resultCode
        }
    }

    // Request permissions + MediaProjection on launch
    LaunchedEffect(Unit) {
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        val notGranted = perms.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) permissionLauncher.launch(notGranted.toTypedArray())

        // Request MediaProjection for WhatsApp audio capture
        val projManager = activity.getSystemService(
            android.content.Context.MEDIA_PROJECTION_SERVICE
        ) as android.media.projection.MediaProjectionManager
        projectionLauncher.launch(projManager.createScreenCaptureIntent())
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            (slideInHorizontally { it } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it } + fadeOut())
        },
        label = "screen_nav"
    ) { screen ->
        when (screen) {
            is Screen.Home -> HomeScreen(
                onOpenPlayer = { item -> currentScreen = Screen.Player(item) }
            )
            is Screen.Player -> PlayerScreen(
                item = screen.item,
                onBack = { currentScreen = Screen.Home }
            )
        }
    }
}

sealed class Screen {
    data object Home : Screen()
    data class Player(val item: RecordingItem) : Screen()
}
