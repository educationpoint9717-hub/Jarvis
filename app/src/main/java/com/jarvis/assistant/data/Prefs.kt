package com.jarvis.assistant.data

import android.content.Context

/** Simple settings store. (Keys are stored in app-private storage.) */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    private fun s(k: String, d: String) = sp.getString(k, d) ?: d

    var geminiKey: String
        get() = s("gemini_key", ""); set(v) = sp.edit().putString("gemini_key", v.trim()).apply()
    var porcupineKey: String
        get() = s("porcupine_key", ""); set(v) = sp.edit().putString("porcupine_key", v.trim()).apply()
    var model: String
        get() = s("model", "gemini-flash-latest"); set(v) = sp.edit().putString("model", v.trim()).apply()
    /** Speech recognition locale: hi-IN (Hindi + Hinglish), ur-PK, en-IN, en-US */
    var language: String
        get() = s("language", "hi-IN"); set(v) = sp.edit().putString("language", v).apply()
    /** Locale used to speak Latin-script replies */
    var ttsLocale: String
        get() = s("tts_locale", "en-IN"); set(v) = sp.edit().putString("tts_locale", v).apply()
    var sensitivity: Float
        get() = sp.getFloat("sensitivity", 0.6f); set(v) = sp.edit().putFloat("sensitivity", v).apply()
    var pitch: Float
        get() = sp.getFloat("pitch", 0.65f); set(v) = sp.edit().putFloat("pitch", v).apply()
    var countryCode: String
        get() = s("country_code", "+91"); set(v) = sp.edit().putString("country_code", v.trim()).apply()
}
