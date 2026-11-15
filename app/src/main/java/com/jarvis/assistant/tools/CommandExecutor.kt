package com.jarvis.assistant.tools

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SmsManager
import android.view.KeyEvent
import com.jarvis.assistant.data.AppDb
import com.jarvis.assistant.data.NoteEntity
import com.jarvis.assistant.data.Prefs
import com.jarvis.assistant.service.JarvisAccessibilityService
import com.jarvis.assistant.service.JarvisNotificationListener
import com.jarvis.assistant.service.ReminderReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Executes the functions Gemini calls. Every function returns a short result string for the model. */
class CommandExecutor(
    private val ctx: Context,
    private val gemini: GeminiClient,
    private val db: AppDb,
    private val prefs: Prefs
) {
    @Volatile var sleepRequested = false
    private val pm = ctx.packageManager
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val acc get() = JarvisAccessibilityService.instance

    suspend fun execute(name: String, a: JSONObject): String = try {
        when (name) {
            "open_app" -> openApp(a.optString("app_name"))
            "make_call" -> makeCall(a.optString("contact"))
            "send_sms" -> sendSms(a.optString("contact"), a.optString("message"))
            "send_whatsapp" -> sendWhatsApp(a.optString("contact"), a.optString("message"))
            "set_wifi" -> toggleViaSettings(a.optBoolean("enabled"), Settings.ACTION_WIFI_SETTINGS, "Wi-Fi")
            "set_bluetooth" -> toggleViaSettings(a.optBoolean("enabled"), Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth")
            "set_flashlight" -> flashlight(a.optBoolean("enabled"))
            "set_volume" -> volume(a)
            "set_brightness" -> brightness(a.optInt("percent", 50))
            "set_alarm" -> alarm(a.optInt("hour"), a.optInt("minute"), a.optString("label", "JARVIS"))
            "set_timer" -> timer(a.optInt("seconds", 60), a.optString("label", "JARVIS"))
            "set_reminder" -> reminder(a.optString("text"), a.optInt("minutes_from_now", 1))
            "read_notifications" -> notifications()
            "play_music" -> playMusic(a.optString("query"), a.optString("app"))
            "media_control" -> mediaControl(a.optString("action"))
            "take_note" -> { db.notes().insert(NoteEntity(text = a.optString("text"))); "Note saved." }
            "read_notes" -> readNotes(a.optString("query"))
            "search_web" -> gemini.grounded(a.optString("query"))
            "ui_read_screen" -> acc?.readScreen() ?: NO_ACC
            "ui_tap" -> acc?.clickText(a.optString("target")) ?: NO_ACC
            "ui_tap_xy" -> acc?.let { if (it.tap(a.optDouble("x").toFloat(), a.optDouble("y").toFloat())) "Tapped." else "Tap failed." } ?: NO_ACC
            "ui_scroll" -> acc?.scroll(a.optString("direction")) ?: NO_ACC
            "ui_type" -> acc?.typeText(a.optString("text")) ?: NO_ACC
            "ui_press" -> acc?.pressKey(a.optString("key")) ?: NO_ACC
            "ui_wait" -> { delay(a.optInt("seconds", 2).coerceIn(1, 5) * 1000L); "Waited." }
            "go_to_sleep" -> { sleepRequested = true; "Going to sleep after your reply." }
            else -> "Unknown function $name."
        }
    } catch (e: SecurityException) {
        "Permission missing: ${e.message}"
    } catch (e: Exception) {
        "Failed: ${e.message}"
    }

    private val NO_ACC = "Accessibility service is not enabled. Ask the user to enable JARVIS in Accessibility settings."

    private fun launch(i: Intent): Boolean {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { ctx.startActivity(i); true } catch (_: Exception) { false }
    }

    // ---------- apps ----------
    private fun score(label: String, q: String): Int = when {
        label == q -> 100
        label.startsWith(q) -> 80
        label.contains(q) -> 60
        q.contains(label) -> 50
        else -> q.split(' ').count { it.length > 2 && label.contains(it) } * 10
    }

    private fun openApp(name: String): String {
        val q = name.lowercase().trim()
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val best = apps.maxByOrNull { score(it.loadLabel(pm).toString().lowercase(), q) }
        if (best == null || score(best.loadLabel(pm).toString().lowercase(), q) == 0) return "No app named '$name' found."
        val i = pm.getLaunchIntentForPackage(best.activityInfo.packageName) ?: return "Cannot launch it."
        return if (launch(i)) "Opened ${best.loadLabel(pm)}." else "Android blocked launching. Grant 'Display over other apps' to JARVIS."
    }

    // ---------- contacts, calls, messages ----------
    private suspend fun resolve(c: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        if (c.isNotEmpty() && c.none { it.isLetter() }) return@withContext c to c
        val p = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val cur = ctx.contentResolver.query(
            p, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$c%"), null
        )
        cur?.use { if (it.moveToFirst()) it.getString(0) to it.getString(1) else null }
    }

    private suspend fun makeCall(contact: String): String {
        val (num, who) = resolve(contact) ?: return "No contact named '$contact'."
        return if (launch(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(num))))) "Calling $who." else "Could not start the call."
    }

    private suspend fun sendSms(contact: String, msg: String): String {
        val (num, who) = resolve(contact) ?: return "No contact named '$contact'."
        val sms = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
        sms.sendMultipartTextMessage(num, null, sms.divideMessage(msg), null, null)
        return "SMS sent to $who."
    }

    private fun waNumber(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        if (raw.trim().startsWith("+")) return digits
        val d = digits.trimStart('0')
        return if (d.length <= 10) prefs.countryCode.filter { it.isDigit() } + d else d
    }

    private suspend fun sendWhatsApp(contact: String, msg: String): String {
        val (num, who) = resolve(contact) ?: return "No contact named '$contact'."
        val uri = Uri.parse("https://wa.me/${waNumber(num)}?text=${Uri.encode(msg)}")
        val ok = listOf("com.whatsapp", "com.whatsapp.w4b").any { pkg ->
            launch(Intent(Intent.ACTION_VIEW, uri).setPackage(pkg))
        }
        if (!ok) return "WhatsApp is not installed."
        val a = acc ?: return "WhatsApp chat with $who is open with the message typed. Tap send (enable Accessibility for auto-send)."
        delay(3000)
        return if (a.clickText("Send").startsWith("Tapped")) "WhatsApp message sent to $who." else "Message typed for $who but I could not press send."
    }

    // ---------- system toggles ----------
    private suspend fun toggleViaSettings(on: Boolean, action: String, label: String): String {
        if (Build.VERSION.SDK_INT < 29 && label == "Wi-Fi") {
            @Suppress("DEPRECATION") ctx.getSystemService(WifiManager::class.java).isWifiEnabled = on
            return "$label ${if (on) "on" else "off"}."
        }
        launch(Intent(action))
        val a = acc ?: return "Opened $label settings. Enable Accessibility so I can flip the switch."
        delay(1500)
        val r = a.setFirstSwitch(on)
        delay(400)
        a.pressKey("back")
        return "$label: $r"
    }

    private fun flashlight(on: Boolean): String {
        val cm = ctx.getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "No flashlight."
        cm.setTorchMode(id, on)
        return "Flashlight ${if (on) "on" else "off"}."
    }

    private fun volume(a: JSONObject): String {
        val stream = when (a.optString("stream").lowercase()) {
            "ring" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            "notification" -> AudioManager.STREAM_NOTIFICATION
            else -> AudioManager.STREAM_MUSIC
        }
        val max = audio.getStreamMaxVolume(stream)
        when (a.optString("direction").lowercase()) {
            "up" -> audio.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            "down" -> audio.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            "mute" -> audio.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
            "unmute" -> audio.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
            else -> {
                if (!a.has("level")) return "Need a level or direction."
                audio.setStreamVolume(stream, (a.optInt("level") * max / 100).coerceIn(0, max), AudioManager.FLAG_SHOW_UI)
            }
        }
        return "Volume adjusted."
    }

    private fun brightness(p: Int): String {
        if (!Settings.System.canWrite(ctx)) {
            launch(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")))
            return "Need 'Modify system settings' permission. Opened the page."
        }
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (p.coerceIn(0, 100) * 255 / 100))
        return "Brightness set to $p percent."
    }

    // ---------- time ----------
    private fun alarm(h: Int, m: Int, label: String): String {
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, h).putExtra(AlarmClock.EXTRA_MINUTES, m)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return if (launch(i)) "Alarm set for %02d:%02d.".format(h, m) else "No clock app found."
    }

    private fun timer(sec: Int, label: String): String {
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, sec).putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return if (launch(i)) "Timer started for $sec seconds." else "No clock app found."
    }

    private fun reminder(text: String, minutes: Int): String {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, (System.currentTimeMillis() % 100000).toInt(),
            Intent(ctx, ReminderReceiver::class.java).putExtra("text", text),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val at = System.currentTimeMillis() + minutes * 60_000L
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        return "Reminder set for $minutes minutes from now."
    }

    // ---------- info / media ----------
    private fun notifications(): String {
        val l = JarvisNotificationListener.instance
            ?: return "Notification access is not granted. Ask the user to enable it in settings."
        return l.snapshot().ifEmpty { listOf("No notifications.") }.joinToString("\n")
    }

    private fun playMusic(q: String, app: String): String {
        val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            .putExtra(SearchManager.QUERY, q)
        if (app.isNotBlank()) {
            val pkg = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .firstOrNull { it.loadLabel(pm).toString().contains(app, true) }?.activityInfo?.packageName
            if (pkg != null) i.setPackage(pkg)
        }
        if (launch(i)) return "Playing $q."
        return if (launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(q)}"))))
            "Opened YouTube search for $q." else "No music app found."
    }

    private fun mediaControl(action: String): String {
        val code = when (action.lowercase()) {
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> return "Unknown media action."
        }
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return "Done."
    }

    private suspend fun readNotes(q: String): String {
        val l = if (q.isBlank()) db.notes().last(10) else db.notes().search(q)
        return if (l.isEmpty()) "No notes." else l.joinToString("\n") { "- ${it.text}" }
    }
}
