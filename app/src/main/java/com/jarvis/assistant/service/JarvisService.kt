package com.jarvis.assistant.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.jarvis.assistant.MainActivity
import com.jarvis.assistant.R
import com.jarvis.assistant.data.AppDb
import com.jarvis.assistant.data.MessageEntity
import com.jarvis.assistant.data.Prefs
import com.jarvis.assistant.tools.CommandExecutor
import com.jarvis.assistant.tools.GeminiClient
import kotlinx.coroutines.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Always-on foreground service: wake word -> listen -> Gemini -> act -> speak. */
class JarvisService : Service() {

    companion object {
        const val ACTION_START = "com.jarvis.START"
        const val ACTION_STOP = "com.jarvis.STOP"
        const val ACTION_WAKE = "com.jarvis.WAKE"
        const val ACTION_RELOAD = "com.jarvis.RELOAD"
        private const val CH = "jarvis_service"
        private const val NID = 1
        @Volatile var instance: JarvisService? = null

        fun send(ctx: Context, action: String) {
            val i = Intent(ctx, JarvisService::class.java).setAction(action)
            if (action == ACTION_START) ContextCompat.startForegroundService(ctx, i) else ctx.startService(i)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var prefs: Prefs
    private lateinit var db: AppDb
    private lateinit var tts: TtsManager
    private lateinit var speech: SpeechManager
    private lateinit var gemini: GeminiClient
    private lateinit var executor: CommandExecutor
    private lateinit var wake: WakeWord
    private var wakeLock: PowerManager.WakeLock? = null
    private var convo: Job? = null
    private var started = false
    private var shuttingDown = false
    private val history = mutableListOf<JSONObject>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CH, "JARVIS", NotificationManager.IMPORTANCE_LOW))
        try {
            ServiceCompat.startForeground(this, NID, notif("Starting…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } catch (e: Exception) {
            JarvisBus.error.value = "Cannot start service: ${e.message}"; stopSelf(); return
        }
        prefs = Prefs(this); db = AppDb.get(this)
        tts = TtsManager(this, prefs); speech = SpeechManager(this)
        gemini = GeminiClient(prefs); executor = CommandExecutor(this, gemini, db, prefs)
        wake = WakeWord(this) { scope.launch { onWake() } }
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jarvis:listen").apply { acquire() }

        scope.launch {
            val old = db.messages().last(12).reversed()
            JarvisBus.transcript.value = old.takeLast(20).map { ChatLine(it.role, it.text) }
            old.dropWhile { it.role != "user" }.forEach {
                history += if (it.role == "user") gemini.userContent(it.text) else gemini.modelContent(it.text)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { shutdown(); stopSelf() }
            ACTION_WAKE -> if (convo?.isActive != true) onWake()
            ACTION_RELOAD -> if (convo?.isActive != true) startWake()
            else -> if (!started) { started = true; startWake() }
        }
        return START_STICKY
    }

    // ---------- wake word ----------
    private fun startWake() {
        scope.launch {
            try {
                JarvisBus.state.value = JState.OFFLINE
                if (!wake.hasModelFiles) {
                    JarvisBus.error.value = "Downloading wake-word model (~40 MB). Keep internet on, please wait…"
                    update("Downloading wake-word model…")
                }
                wake.prepare()
                wake.resume()
                JarvisBus.error.value = null
                JarvisBus.state.value = JState.SLEEPING
                update("Sleeping. Say “Jarvis”")
            } catch (e: Exception) {
                JarvisBus.error.value = "Wake word error: ${e.message}"
                JarvisBus.state.value = JState.OFFLINE
                update("Wake word error")
            }
        }
    }

    private fun onWake() {
        if (convo?.isActive == true) return
        wake.pause()
        convo = scope.launch {
            try {
                beep()
                say("Yes, sir?")
                conversation()
            } finally {
                if (!shuttingDown) { wake.resume(); JarvisBus.state.value = JState.SLEEPING; JarvisBus.partial.value = ""; update("Sleeping. Say “Jarvis”") }
            }
        }
    }

    private suspend fun conversation() {
        var misses = 0
        while (currentCoroutineContext().isActive) {
            JarvisBus.state.value = JState.LISTENING
            update("Listening…")
            val heard = speech.listenOnce(prefs.language) { JarvisBus.partial.value = it }
            JarvisBus.partial.value = ""
            if (heard.isNullOrBlank()) { if (++misses >= 3) return; delay(300); continue }
            misses = 0
            JarvisBus.add("user", heard)
            if (isSleep(heard)) { say("Going offline, sir"); return }
            handle(heard)
            if (executor.sleepRequested) { executor.sleepRequested = false; return }
        }
    }

    private fun isSleep(t: String): Boolean {
        val s = t.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), "").trim()
        if (s == "stop" || s == "jarvis stop" || s == "ruk jao") return true
        return listOf(
            "band ho jao", "band ho ja", "go to sleep", "stop listening", "sleep mode", "so jao", "so ja",
            "chup ho jao", "jarvis band", "बंद हो जाओ", "बंद हो जा", "सो जाओ", "بند ہو جاؤ", "سو جاؤ"
        ).any { s.contains(it) }
    }

    // ---------- brain ----------
    private suspend fun handle(text: String) {
        JarvisBus.state.value = JState.THINKING
        update("Thinking…")
        val start = history.size
        history += gemini.userContent(text)
        val reply = try {
            gemini.run(history, systemPrompt()) { n, a -> executor.execute(n, a) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            JarvisBus.error.value = e.message
            "Sorry sir, I could not reach my servers."
        }
        while (history.size > start) history.removeAt(history.lastIndex)   // drop tool chatter
        history += gemini.userContent(text); history += gemini.modelContent(reply)
        while (history.size > 24) history.removeAt(0)
        while (history.isNotEmpty() && history[0].optString("role") != "user") history.removeAt(0)

        db.messages().insert(MessageEntity(role = "user", text = text))
        db.messages().insert(MessageEntity(role = "jarvis", text = reply))
        JarvisBus.add("jarvis", reply)
        say(reply)
    }

    private suspend fun systemPrompt(): String {
        val notes = db.notes().last(10).joinToString("\n") { "- ${it.text}" }.ifEmpty { "(none)" }
        val now = SimpleDateFormat("EEEE, d MMMM yyyy, HH:mm", Locale.ENGLISH).format(Date())
        return """
You are J.A.R.V.I.S., a calm, witty, highly capable voice assistant like Tony Stark's. Address the user as "sir".
Now: $now. Replies are spoken aloud: 1 to 3 short sentences, no markdown, no lists, no emoji.
Reply in the user's language. For Hindi/Hinglish input reply in Hinglish using the Roman alphabet; for Urdu use Urdu script; otherwise English.
Use functions to act on the phone. Never claim an action succeeded unless the function result says so; if it fails, say what is missing briefly.
To operate any other app: open_app, ui_wait, ui_read_screen, then ui_tap / ui_type / ui_scroll / ui_press, re-reading the screen after each step.
For current facts, news, weather, sports or unknown topics call search_web. If the user wants you to stop listening, call go_to_sleep.
If a contact is ambiguous or a message is missing, ask a short question instead of guessing.
Saved notes about the user:
$notes
""".trimIndent()
    }

    // ---------- helpers ----------
    private suspend fun say(text: String) {
        JarvisBus.state.value = JState.SPEAKING
        update("Speaking…")
        tts.speak(text)
    }

    fun speakExternal(text: String) {
        if (convo?.isActive == true) return
        scope.launch {
            say(text)
            JarvisBus.add("jarvis", text)
            JarvisBus.state.value = if (wake.isReady) JState.SLEEPING else JState.OFFLINE
        }
    }

    private fun beep() = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 80).startTone(ToneGenerator.TONE_PROP_ACK, 150)
    } catch (_: Exception) {}

    private fun notif(text: String): Notification {
        val pi = { a: String -> PendingIntent.getService(this, a.hashCode(), Intent(this, JarvisService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE) }
        return NotificationCompat.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_jarvis).setContentTitle("JARVIS").setContentText(text)
            .setOngoing(true).setOnlyAlertOnce(true)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .addAction(0, "Wake", pi(ACTION_WAKE)).addAction(0, "Stop", pi(ACTION_STOP))
            .build()
    }

    private fun update(text: String) =
        getSystemService(NotificationManager::class.java).notify(NID, notif(text))

    private fun shutdown() {
        shuttingDown = true
        convo?.cancel()
        if (::wake.isInitialized) wake.release()
        JarvisBus.state.value = JState.OFFLINE
    }

    override fun onDestroy() {
        shutdown()
        scope.cancel()
        if (::tts.isInitialized) tts.shutdown()
        wakeLock?.let { if (it.isHeld) it.release() }
        instance = null
        super.onDestroy()
    }
}
