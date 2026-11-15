package com.jarvis.assistant

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.jarvis.assistant.service.JState
import com.jarvis.assistant.service.JarvisBus
import com.jarvis.assistant.service.JarvisService
import com.jarvis.assistant.ui.HudScreen
import com.jarvis.assistant.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private val tick = mutableIntStateOf(0)
    private var startAfterGrant = false

    private val askPerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        tick.intValue++
        if (startAfterGrant && r[Manifest.permission.RECORD_AUDIO] != false) JarvisService.send(this, JarvisService.ACTION_START)
        startAfterGrant = false
    }

    private fun perms(): Array<String> = buildList {
        add(Manifest.permission.RECORD_AUDIO); add(Manifest.permission.CALL_PHONE)
        add(Manifest.permission.SEND_SMS); add(Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF00E5FF), background = Color.Black, surface = Color(0xFF061A26))) {
                var screen by remember { mutableStateOf("hud") }
                if (screen == "hud") HudScreen(
                    onSettings = { screen = "settings" },
                    onToggle = {
                        if (JarvisBus.state.value == JState.OFFLINE) { startAfterGrant = true; askPerms.launch(perms()) }
                        else JarvisService.send(this, JarvisService.ACTION_STOP)
                    },
                    onWake = { JarvisService.send(this, JarvisService.ACTION_WAKE) }
                ) else SettingsScreen(tick.intValue, { screen = "hud" }) { askPerms.launch(perms()) }
            }
        }
    }

    override fun onResume() { super.onResume(); tick.intValue++ }
}
