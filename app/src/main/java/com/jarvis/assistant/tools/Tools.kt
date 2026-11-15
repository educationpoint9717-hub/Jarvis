package com.jarvis.assistant.tools

import org.json.JSONArray
import org.json.JSONObject

private data class P(val name: String, val type: String, val desc: String, val required: Boolean = true)

private fun fn(name: String, desc: String, vararg params: P): JSONObject {
    val o = JSONObject().put("name", name).put("description", desc)
    if (params.isNotEmpty()) {
        val props = JSONObject()
        val req = JSONArray()
        params.forEach {
            props.put(it.name, JSONObject().put("type", it.type).put("description", it.desc))
            if (it.required) req.put(it.name)
        }
        o.put("parameters", JSONObject().put("type", "OBJECT").put("properties", props).put("required", req))
    }
    return o
}

/** Function declarations sent to Gemini. */
object Tools {
    fun declarations(): JSONArray = JSONArray().apply {
        put(fn("open_app", "Open an installed app by its name.", P("app_name", "STRING", "App name, e.g. WhatsApp, YouTube, Camera")))
        put(fn("make_call", "Place a phone call.", P("contact", "STRING", "Contact name or phone number")))
        put(fn("send_sms", "Send an SMS text message.", P("contact", "STRING", "Contact name or phone number"), P("message", "STRING", "Message text")))
        put(fn("send_whatsapp", "Send a WhatsApp message.", P("contact", "STRING", "Contact name or phone number"), P("message", "STRING", "Message text")))
        put(fn("set_wifi", "Turn Wi-Fi on or off.", P("enabled", "BOOLEAN", "true = on, false = off")))
        put(fn("set_bluetooth", "Turn Bluetooth on or off.", P("enabled", "BOOLEAN", "true = on, false = off")))
        put(fn("set_flashlight", "Turn the flashlight on or off.", P("enabled", "BOOLEAN", "true = on, false = off")))
        put(fn("set_volume", "Change volume. Give level (0-100) or direction.",
            P("level", "INTEGER", "Volume percent 0-100", false),
            P("direction", "STRING", "up, down, mute or unmute", false),
            P("stream", "STRING", "music (default), ring, alarm, notification", false)))
        put(fn("set_brightness", "Set screen brightness.", P("percent", "INTEGER", "0-100")))
        put(fn("set_alarm", "Set an alarm at a time of day.", P("hour", "INTEGER", "0-23"), P("minute", "INTEGER", "0-59"), P("label", "STRING", "Alarm label", false)))
        put(fn("set_timer", "Start a countdown timer.", P("seconds", "INTEGER", "Duration in seconds"), P("label", "STRING", "Timer label", false)))
        put(fn("set_reminder", "Remind the user (spoken + notification) after some minutes.", P("text", "STRING", "What to remind"), P("minutes_from_now", "INTEGER", "Minutes from now")))
        put(fn("read_notifications", "Read the user's current notifications."))
        put(fn("play_music", "Play a song/artist/playlist.", P("query", "STRING", "Song, artist or playlist"), P("app", "STRING", "Preferred app name, e.g. Spotify, YouTube Music", false)))
        put(fn("media_control", "Control current media playback.", P("action", "STRING", "play, pause, next, previous or stop")))
        put(fn("take_note", "Save a note or fact to remember.", P("text", "STRING", "Note text")))
        put(fn("read_notes", "Read saved notes.", P("query", "STRING", "Optional search word", false)))
        put(fn("search_web", "Search the web (Google Search grounding) for current facts, news, weather, sports, any topic.", P("query", "STRING", "What to look up, in full")))
        put(fn("ui_read_screen", "Read visible text and tappable elements of the current screen. Call this before tapping something in another app."))
        put(fn("ui_tap", "Tap an on-screen element by its visible text or description.", P("target", "STRING", "Text or description of the element")))
        put(fn("ui_tap_xy", "Tap at screen coordinates (pixels).", P("x", "INTEGER", "X"), P("y", "INTEGER", "Y")))
        put(fn("ui_scroll", "Scroll or swipe the screen.", P("direction", "STRING", "up, down, left or right (down = see content further down)")))
        put(fn("ui_type", "Type text into the focused or first text field.", P("text", "STRING", "Text to type")))
        put(fn("ui_press", "Press a system key.", P("key", "STRING", "back, home, recents, notifications, quick_settings, lock, enter")))
        put(fn("ui_wait", "Wait for an app to load.", P("seconds", "INTEGER", "1-5")))
        put(fn("go_to_sleep", "Stop listening and go back to wake-word-only mode."))
    }
}
