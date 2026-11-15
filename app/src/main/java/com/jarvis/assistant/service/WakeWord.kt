package com.jarvis.assistant.service

import android.content.Context
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import com.jarvis.assistant.data.Prefs

/** Porcupine "Jarvis" built-in keyword. Runs its own mic loop; pause() releases the mic. */
class WakeWord(private val ctx: Context, private val onWake: () -> Unit) {
    private var mgr: PorcupineManager? = null

    fun start(prefs: Prefs) {
        release()
        require(prefs.porcupineKey.isNotBlank()) { "Set your Porcupine AccessKey in Settings" }
        mgr = PorcupineManager.Builder()
            .setAccessKey(prefs.porcupineKey)
            .setKeyword(Porcupine.BuiltInKeyword.JARVIS)
            .setSensitivity(prefs.sensitivity)
            .build(ctx, PorcupineManagerCallback { onWake() })
        mgr?.start()
    }

    fun pause() { try { mgr?.stop() } catch (_: Exception) {} }
    fun resume() { try { mgr?.start() } catch (_: Exception) {} }
    fun release() {
        try { mgr?.stop(); mgr?.delete() } catch (_: Exception) {}
        mgr = null
    }
    val isReady get() = mgr != null
}
