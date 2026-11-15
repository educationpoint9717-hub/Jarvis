package com.jarvis.assistant.tools

import com.jarvis.assistant.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiClient(private val prefs: Prefs) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private suspend fun post(body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        require(prefs.geminiKey.isNotBlank()) { "Set your Gemini API key in Settings" }
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/${prefs.model}:generateContent")
            .header("x-goog-api-key", prefs.geminiKey)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) error("Gemini ${r.code}: ${s.take(250)}")
            JSONObject(s)
        }
    }

    private fun sys(text: String) = JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text)))

    fun userContent(text: String): JSONObject =
        JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", text)))

    fun modelContent(text: String): JSONObject =
        JSONObject().put("role", "model").put("parts", JSONArray().put(JSONObject().put("text", text)))

    private fun textOf(parts: JSONArray?): String {
        if (parts == null) return ""
        val sb = StringBuilder()
        for (i in 0 until parts.length()) parts.getJSONObject(i).optString("text").takeIf { it.isNotEmpty() }?.let { sb.append(it) }
        return sb.toString().trim()
    }

    /**
     * Agent loop: sends [contents], executes any function calls via [exec], feeds results back,
     * and returns the final spoken reply. The raw model content (incl. thought signatures) is
     * kept in [contents] so newer Gemini models accept the follow-up turn.
     */
    suspend fun run(
        contents: MutableList<JSONObject>,
        systemPrompt: String,
        exec: suspend (String, JSONObject) -> String
    ): String {
        repeat(10) {
            val req = JSONObject()
                .put("systemInstruction", sys(systemPrompt))
                .put("contents", JSONArray(contents))
                .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", Tools.declarations())))
                .put("generationConfig", JSONObject().put("temperature", 0.7))
            val resp = post(req)
            val content = resp.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")
                ?: return "I have nothing to add, sir."
            contents.add(content)
            val parts = content.optJSONArray("parts") ?: JSONArray()

            val results = JSONArray()
            for (i in 0 until parts.length()) {
                val fc = parts.getJSONObject(i).optJSONObject("functionCall") ?: continue
                val name = fc.getString("name")
                val result = exec(name, fc.optJSONObject("args") ?: JSONObject())
                results.put(JSONObject().put("functionResponse", JSONObject()
                    .put("name", name)
                    .put("response", JSONObject().put("result", result))))
            }
            if (results.length() == 0) return textOf(parts).ifEmpty { "Done, sir." }
            contents.add(JSONObject().put("role", "user").put("parts", results))
        }
        return "That took too many steps, sir."
    }

    /** Google Search grounded answer (separate call, works with every model). */
    suspend fun grounded(query: String): String {
        val body = JSONObject()
            .put("systemInstruction", sys("Answer in 2 to 4 short spoken sentences. No markdown, no lists."))
            .put("contents", JSONArray().put(userContent(query)))
            .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
        val r = post(body)
        return textOf(r.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts"))
            .ifEmpty { "I found nothing, sir." }
    }
}
