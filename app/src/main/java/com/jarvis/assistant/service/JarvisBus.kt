package com.jarvis.assistant.service

import kotlinx.coroutines.flow.MutableStateFlow

enum class JState { OFFLINE, SLEEPING, LISTENING, THINKING, SPEAKING }

data class ChatLine(val role: String, val text: String)

/** Shared state between the service and the UI. */
object JarvisBus {
    val state = MutableStateFlow(JState.OFFLINE)
    val partial = MutableStateFlow("")
    val error = MutableStateFlow<String?>(null)
    val transcript = MutableStateFlow<List<ChatLine>>(emptyList())

    fun add(role: String, text: String) {
        transcript.value = (transcript.value + ChatLine(role, text)).takeLast(60)
    }
}
