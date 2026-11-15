package com.jarvis.assistant.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.jarvis.assistant.data.Prefs
import com.jarvis.assistant.service.JarvisAccessibilityService
import com.jarvis.assistant.service.JarvisService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(refresh: Int, onBack: () -> Unit, onGrantRuntime: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var gemini by remember { mutableStateOf(prefs.geminiKey) }
    var model by remember { mutableStateOf(prefs.model) }
    var lang by remember { mutableStateOf(prefs.language) }
    var tts by remember { mutableStateOf(prefs.ttsLocale) }
    var pitch by remember { mutableFloatStateOf(prefs.pitch) }
    var cc by remember { mutableStateOf(prefs.countryCode) }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF02070D)).statusBarsPadding().navigationBarsPadding()
            .padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("SETTINGS", color = Cyan, style = MaterialTheme.typography.titleLarge)
            TextButton(onBack) { Text("Back") }
        }
        Field("Gemini API key", gemini, true) { gemini = it }
        Field("Gemini model", model, false) { model = it }
        Field("Default country code (WhatsApp)", cc, false) { cc = it }

        Text("Speech language", color = Color.White)
        Chips(listOf("hi-IN" to "Hindi / Hinglish", "ur-PK" to "Urdu", "en-IN" to "English (IN)", "en-US" to "English (US)"), lang) { lang = it }
        Text("Voice accent (English replies)", color = Color.White)
        Chips(listOf("en-IN" to "Indian", "en-GB" to "British", "en-US" to "American"), tts) { tts = it }

        Text("Voice depth (pitch): %.2f  (lower = deeper)".format(pitch), color = Color.White)
        Slider(pitch, { pitch = it }, valueRange = 0.3f..1.2f)

        Button(onClick = {
            prefs.geminiKey = gemini; prefs.model = model.ifBlank { "gemini-flash-latest" }
            prefs.language = lang; prefs.ttsLocale = tts; prefs.pitch = pitch; prefs.countryCode = cc
            JarvisService.send(ctx, JarvisService.ACTION_RELOAD)
            onBack()
        }, Modifier.fillMaxWidth()) { Text("SAVE") }

        HorizontalDivider(color = Cyan.copy(0.3f))
        Text("PERMISSIONS", color = Cyan, style = MaterialTheme.typography.titleMedium)
        key(refresh) {
            Perm("Mic, notifications, calls, SMS, contacts", null, "Grant", onGrantRuntime)
            Perm("Accessibility (control other apps)", accessibilityOn(ctx), "Open") { open(ctx, Settings.ACTION_ACCESSIBILITY_SETTINGS) }
            Perm("Notification access (read notifications)", NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName), "Open") { open(ctx, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
            Perm("Display over other apps (launch apps from background)", Settings.canDrawOverlays(ctx), "Open") { open(ctx, Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true) }
            Perm("Modify system settings (brightness)", Settings.System.canWrite(ctx), "Open") { open(ctx, Settings.ACTION_MANAGE_WRITE_SETTINGS, true) }
            Perm("Battery: unrestricted", ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName), "Open") { open(ctx, Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, true) }
            if (Build.VERSION.SDK_INT >= 31)
                Perm("Exact alarms (reminders)", ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(), "Open") { open(ctx, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, true) }
        }
    }
}

@Composable
private fun Field(label: String, v: String, secret: Boolean, set: (String) -> Unit) =
    OutlinedTextField(
        v, set, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None
    )

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(opts: List<Pair<String, String>>, sel: String, pick: (String) -> Unit) =
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opts.forEach { (code, name) -> FilterChip(sel == code, { pick(code) }, { Text(name) }) }
    }

@Composable
private fun Perm(label: String, ok: Boolean?, btn: String, onClick: () -> Unit) =
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text((if (ok == true) "✓ " else if (ok == false) "✗ " else "• ") + label, color = if (ok == true) Cyan else Color.White, modifier = Modifier.weight(1f))
        TextButton(onClick) { Text(btn) }
    }

private fun open(ctx: Context, action: String, withPkg: Boolean = false) {
    val i = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (withPkg) i.data = Uri.parse("package:${ctx.packageName}")
    try { ctx.startActivity(i) } catch (_: Exception) { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun accessibilityOn(ctx: Context): Boolean {
    if (JarvisAccessibilityService.instance != null) return true
    val s = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return s.contains(ctx.packageName)
}
