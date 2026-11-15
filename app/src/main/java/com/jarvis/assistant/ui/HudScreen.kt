package com.jarvis.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.service.JState
import com.jarvis.assistant.service.JarvisBus

val Cyan = Color(0xFF00E5FF)

@Composable
fun HudScreen(onSettings: () -> Unit, onToggle: () -> Unit, onWake: () -> Unit) {
    val state by JarvisBus.state.collectAsState()
    val partial by JarvisBus.partial.collectAsState()
    val error by JarvisBus.error.collectAsState()
    val lines by JarvisBus.transcript.collectAsState()
    val list = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) list.animateScrollToItem(lines.lastIndex) }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF02070D), Color(0xFF061A26))))
            .statusBarsPadding().navigationBarsPadding().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("J.A.R.V.I.S", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 22.sp, letterSpacing = 6.sp)
            IconButton(onSettings) { Icon(Icons.Filled.Settings, "Settings", tint = Cyan) }
        }
        Spacer(Modifier.height(12.dp))
        ArcReactor(state, Modifier.size(240.dp).clickable { if (state == JState.OFFLINE) onToggle() else onWake() })
        Spacer(Modifier.height(10.dp))
        Text(
            when (state) {
                JState.OFFLINE -> "OFFLINE"; JState.SLEEPING -> "STANDBY · SAY “JARVIS”"
                JState.LISTENING -> "LISTENING"; JState.THINKING -> "PROCESSING"; JState.SPEAKING -> "SPEAKING"
            },
            color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 13.sp, letterSpacing = 3.sp
        )
        if (partial.isNotEmpty()) Text(partial, color = Color.White.copy(0.7f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        error?.let { Text(it, color = Color(0xFFFF5252), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }

        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(lines) { l ->
                val me = l.role == "user"
                Row(Modifier.fillMaxWidth(), if (me) Arrangement.End else Arrangement.Start) {
                    Text(
                        (if (me) "YOU  " else "JARVIS  ") + l.text,
                        color = if (me) Color.White else Cyan, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                        modifier = Modifier.background(Color(0x2200E5FF), RoundedCornerShape(10.dp)).padding(10.dp).widthIn(max = 300.dp)
                    )
                }
            }
        }
        OutlinedButton(onClick = onToggle) {
            Text(if (state == JState.OFFLINE) "ACTIVATE" else "TAKE OFFLINE", color = Cyan, fontFamily = FontFamily.Monospace)
        }
    }
}
