package com.jarvis.assistant.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Free offline wake word using Vosk. The recognizer only knows the word "jarvis"
 * (plus [unk] for everything else). The small English model (~40 MB) is downloaded once.
 */
class WakeWord(private val ctx: Context, private val onWake: () -> Unit) {

    private val modelName = "vosk-model-small-en-us-0.15"
    private val modelUrl = "https://alphacephei.com/vosk/models/$modelName.zip"
    private val modelDir get() = File(ctx.filesDir, modelName)

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var service: SpeechService? = null
    @Volatile private var fired = false

    val isReady get() = model != null
    val hasModelFiles get() = File(modelDir, "am/final.mdl").exists()

    /** Downloads (first time only) and loads the model. */
    suspend fun prepare() {
        if (model != null) return
        withContext(Dispatchers.IO) {
            if (!hasModelFiles) download()
            model = Model(modelDir.absolutePath)
        }
    }

    private fun download() {
        val zip = File(ctx.cacheDir, "model.zip")
        URL(modelUrl).openStream().use { input -> zip.outputStream().use { input.copyTo(it) } }
        val base = ctx.filesDir.canonicalPath
        ZipInputStream(zip.inputStream()).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                val out = File(ctx.filesDir, e.name)
                require(out.canonicalPath.startsWith(base)) { "Bad zip entry" }
                if (e.isDirectory) out.mkdirs() else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zis.copyTo(it) }
                }
                e = zis.nextEntry
            }
        }
        zip.delete()
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResult(h: String?) = check(h)
        override fun onResult(h: String?) = check(h)
        override fun onFinalResult(h: String?) {}
        override fun onError(e: Exception?) {}
        override fun onTimeout() {}
    }

    private fun check(h: String?) {
        if (h != null && !fired && h.contains("jarvis", ignoreCase = true)) {
            fired = true
            onWake()
        }
    }

    /** Start listening for the wake word (holds the mic). */
    fun resume() {
        val m = model ?: return
        if (service != null) return
        fired = false
        val rec = Recognizer(m, 16000.0f, "[\"jarvis\", \"[unk]\"]")
        recognizer = rec
        service = SpeechService(rec, 16000.0f).also { it.startListening(listener) }
    }

    /** Release the mic so SpeechRecognizer can use it. */
    fun pause() {
        try { service?.stop(); service?.shutdown() } catch (_: Exception) {}
        service = null
        try { recognizer?.close() } catch (_: Exception) {}
        recognizer = null
    }

    fun release() {
        pause()
        try { model?.close() } catch (_: Exception) {}
        model = null
    }
}
