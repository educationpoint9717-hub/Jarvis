package com.jarvis.assistant.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.jarvis.assistant.data.Prefs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/** Deep, male-sounding TTS (low pitch + male voice when the engine has one). */
class TtsManager(ctx: Context, private val prefs: Prefs) {
    private val ready = CompletableDeferred<Boolean>()
    private val tts: TextToSpeech = TextToSpeech(ctx.applicationContext) { ready.complete(it == TextToSpeech.SUCCESS) }

    private fun localeFor(text: String): Locale = when {
        text.any { it in '\u0900'..'\u097F' } -> Locale.forLanguageTag("hi-IN")
        text.any { it in '\u0600'..'\u06FF' } -> Locale.forLanguageTag("ur-PK")
        else -> Locale.forLanguageTag(prefs.ttsLocale)
    }

    suspend fun speak(raw: String) {
        val text = raw.replace(Regex("[*_`#>]"), "").trim()
        if (text.isEmpty()) return
        if (withTimeoutOrNull(4000) { ready.await() } != true) return

        val loc = localeFor(text)
        tts.language = loc
        tts.voices
            ?.filter { it.locale.language == loc.language && !it.isNetworkConnectionRequired }
            ?.firstOrNull { it.name.contains("male", true) && !it.name.contains("female", true) }
            ?.let { tts.voice = it }
        tts.setPitch(prefs.pitch)
        tts.setSpeechRate(0.95f)

        suspendCancellableCoroutine { cont ->
            val id = UUID.randomUUID().toString()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(u: String?) {}
                override fun onDone(u: String?) { if (cont.isActive) cont.resume(Unit) }
                @Deprecated("Deprecated in Java")
                override fun onError(u: String?) { if (cont.isActive) cont.resume(Unit) }
            })
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            cont.invokeOnCancellation { tts.stop() }
        }
    }

    fun shutdown() { tts.stop(); tts.shutdown() }
}
