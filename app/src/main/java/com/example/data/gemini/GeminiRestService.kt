package com.example.data.gemini

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Service for Google Search Grounding with gemini-3.8-flash and
 * Text-to-Speech audio generation with gemini-3.8-flash-tts.
 */
class GeminiRestService(private val apiKey: String) {

    companion object {
        private const val TAG = "GeminiRestService"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Generates a grounded response using gemini-3.8-flash with Google Search.
     */
    suspend fun generateGroundedResponse(prompt: String, sassLevel: Int = 2, languageId: String = "en"): GroundedResult =
        withContext(Dispatchers.IO) {
            val modelName = "gemini-3.8-flash"
            val url = "$BASE_URL/$modelName:generateContent?key=$apiKey"

            val requestBodyJson = JSONObject().apply {
                val contents = JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt))
                    )
                )
                put("contents", contents)

                val systemInstruction = JSONObject().apply {
                    val parts = JSONArray().put(
                        JSONObject().put("text", MahiPersonality.buildSystemInstruction(sassLevel, languageId))
                    )
                    put("parts", parts)
                }
                put("systemInstruction", systemInstruction)

                // Google Search Grounding Tool
                val tools = JSONArray().put(
                    JSONObject().put("googleSearch", JSONObject())
                )
                put("tools", tools)
            }

            try {
                val request = Request.Builder()
                    .url(url)
                    .post(requestBodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseStr = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Search Grounding API error: ${response.code} $responseStr")
                        return@withContext GroundedResult(
                            text = "Hmm, something tripped me up while searching that, darling. Try again!",
                            searchSources = emptyList()
                        )
                    }

                    val json = JSONObject(responseStr)
                    val candidates = json.optJSONArray("candidates")
                    val firstCandidate = candidates?.optJSONObject(0)
                    val content = firstCandidate?.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")

                    val textBuilder = StringBuilder()
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            if (part.has("text")) {
                                textBuilder.append(part.getString("text"))
                            }
                        }
                    }

                    // Extract grounding sources if available
                    val sources = mutableListOf<String>()
                    val groundingMetadata = firstCandidate?.optJSONObject("groundingMetadata")
                    val webSearchQueries = groundingMetadata?.optJSONArray("webSearchQueries")
                    if (webSearchQueries != null) {
                        for (i in 0 until webSearchQueries.length()) {
                            sources.add(webSearchQueries.getString(i))
                        }
                    }

                    val text = textBuilder.toString().ifEmpty {
                        "Darling, you've got me speechless. Give me another question!"
                    }

                    GroundedResult(text = text, searchSources = sources)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in generateGroundedResponse", e)
                GroundedResult(
                    text = "Oops, my wifi signals got a little sassy. Ask me again!",
                    searchSources = emptyList()
                )
            }
        }

    /**
     * Converts text to speech audio using gemini-3.8-flash-tts or gemini-2.5-flash-preview-tts.
     * Returns Base64-encoded PCM audio data if successful.
     */
    suspend fun generateSpeechAudio(text: String, voiceName: String = "Aoede"): String? =
        withContext(Dispatchers.IO) {
            // Priority model list: gemini-3.8-flash-tts -> fallback to gemini-2.5-flash-preview-tts
            val models = listOf("gemini-3.8-flash-tts", "gemini-2.5-flash-preview-tts")

            for (model in models) {
                val url = "$BASE_URL/$model:generateContent?key=$apiKey"
                val requestBodyJson = JSONObject().apply {
                    val contents = JSONArray().put(
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", text))
                        )
                    )
                    put("contents", contents)

                    val generationConfig = JSONObject().apply {
                        put("responseModalities", JSONArray().put("AUDIO"))
                        val speechConfig = JSONObject().apply {
                            val voiceConfig = JSONObject().apply {
                                val prebuilt = JSONObject().apply {
                                    put("voiceName", voiceName)
                                }
                                put("prebuiltVoiceConfig", prebuilt)
                            }
                            put("voiceConfig", voiceConfig)
                        }
                        put("speechConfig", speechConfig)
                    }
                    put("generationConfig", generationConfig)
                }

                try {
                    val request = Request.Builder()
                        .url(url)
                        .post(requestBodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val responseStr = response.body?.string() ?: ""
                            val json = JSONObject(responseStr)
                            val candidates = json.optJSONArray("candidates")
                            val candidate = candidates?.optJSONObject(0)
                            val content = candidate?.optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            if (parts != null) {
                                for (i in 0 until parts.length()) {
                                    val part = parts.getJSONObject(i)
                                    val inlineData = part.optJSONObject("inlineData")
                                    val audioData = inlineData?.optString("data")
                                    if (!audioData.isNullOrEmpty()) {
                                        return@withContext audioData
                                    }
                                }
                            }
                        } else {
                            Log.w(TAG, "TTS Model $model returned code ${response.code}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed TTS with model $model: ${e.message}")
                }
            }
            null
        }
}

data class GroundedResult(
    val text: String,
    val searchSources: List<String>
)
