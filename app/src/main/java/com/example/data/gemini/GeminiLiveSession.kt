package com.example.data.gemini

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Manages the bidirectional WebSocket session for Gemini Live API.
 * Supports realtime audio streaming (16kHz PCM upload, 24kHz PCM download),
 * function calling, interruptions, and transcripts.
 */
class GeminiLiveSession(
    private val apiKey: String,
    private val model: String = "models/gemini-2.5-flash-native-audio-preview-12-2025"
) {

    companion object {
        private const val TAG = "GeminiLiveSession"
        private const val WS_BASE_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
    }

    private var webSocket: WebSocket? = null
    private var isConnected = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite for live stream
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    var onConnected: (() -> Unit)? = null
    var onAudioChunkReceived: ((String) -> Unit)? = null
    var onTranscriptReceived: ((String, Boolean) -> Unit)? = null
    var onInterrupted: (() -> Unit)? = null
    var onTurnComplete: (() -> Unit)? = null
    var onToolCall: ((name: String, args: Map<String, Any>, id: String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onClosed: ((String) -> Unit)? = null

    fun connect(voiceName: String = "Aoede", sassLevel: Int = 2, languageId: String = "en") {
        if (isConnected) return

        val url = "$WS_BASE_URL?key=$apiKey"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                Log.d(TAG, "WebSocket connected successfully")
                sendInitialSetup(voiceName, sassLevel, languageId)
                onConnected?.invoke()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                onError?.invoke(t.message ?: "Connection failure")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                Log.d(TAG, "WebSocket closed: $code / $reason")
                onClosed?.invoke(reason)
            }
        })
    }

    private fun sendInitialSetup(voiceName: String, sassLevel: Int, languageId: String) {
        try {
            val setupObj = JSONObject().apply {
                put("model", model)

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

                val systemInstruction = JSONObject().apply {
                    val parts = JSONArray().put(
                        JSONObject().put("text", MahiPersonality.buildSystemInstruction(sassLevel, languageId))
                    )
                    put("parts", parts)
                }
                put("systemInstruction", systemInstruction)

                // Declare tools (openWebsite & googleSearch)
                val openWebsiteFunc = JSONObject().apply {
                    put("name", "openWebsite")
                    put("description", "Opens a website URL in the phone browser")
                    val params = JSONObject().apply {
                        put("type", "OBJECT")
                        val props = JSONObject().apply {
                            props(this)
                        }
                        put("properties", props)
                        put("required", JSONArray().put("url"))
                    }
                    put("parameters", params)
                }

                val googleSearchFunc = JSONObject().apply {
                    put("name", "googleSearch")
                    put("description", "Searches Google for live or recent information")
                    val params = JSONObject().apply {
                        put("type", "OBJECT")
                        val props = JSONObject().apply {
                            val queryProp = JSONObject().apply {
                                put("type", "STRING")
                                put("description", "The search query string")
                            }
                            put("query", queryProp)
                        }
                        put("properties", props)
                        put("required", JSONArray().put("query"))
                    }
                    put("parameters", params)
                }

                val toolDeclarations = JSONArray().apply {
                    put(openWebsiteFunc)
                    put(googleSearchFunc)
                }
                val toolObj = JSONObject().apply {
                    put("functionDeclarations", toolDeclarations)
                }
                put("tools", JSONArray().put(toolObj))
            }

            val payload = JSONObject().apply {
                put("setup", setupObj)
            }

            webSocket?.send(payload.toString())
            Log.d(TAG, "Setup packet sent")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending setup packet", e)
        }
    }

    private fun props(target: JSONObject) {
        val urlProp = JSONObject().apply {
            put("type", "STRING")
            put("description", "The website URL to launch, e.g. https://youtube.com")
        }
        target.put("url", urlProp)
    }

    /**
     * Sends realtime PCM audio chunk (16kHz PCM16) encoded in Base64.
     */
    fun sendRealtimeAudio(base64Chunk: String) {
        if (!isConnected || webSocket == null) return

        try {
            val chunkObj = JSONObject().apply {
                put("mimeType", "audio/pcm;rate=16000")
                put("data", base64Chunk)
            }
            val realtimeInput = JSONObject().apply {
                put("mediaChunks", JSONArray().put(chunkObj))
            }
            val payload = JSONObject().apply {
                put("realtimeInput", realtimeInput)
            }
            webSocket?.send(payload.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio chunk", e)
        }
    }

    /**
     * Sends tool response back to Gemini Live session.
     */
    fun sendToolResponse(callId: String, result: String) {
        if (!isConnected || webSocket == null) return

        try {
            val responseObj = JSONObject().apply {
                val output = JSONObject().apply {
                    put("result", result)
                }
                put("response", JSONObject().put("output", output))
                put("id", callId)
            }
            val toolResponse = JSONObject().apply {
                put("functionResponses", JSONArray().put(responseObj))
            }
            val payload = JSONObject().apply {
                put("toolResponse", toolResponse)
            }
            webSocket?.send(payload.toString())
            Log.d(TAG, "Sent toolResponse for $callId: $result")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending tool response", e)
        }
    }

    private fun handleServerMessage(jsonText: String) {
        try {
            val root = JSONObject(jsonText)

            if (root.has("serverContent")) {
                val serverContent = root.getJSONObject("serverContent")

                if (serverContent.optBoolean("interrupted", false)) {
                    Log.d(TAG, "Model response was interrupted")
                    onInterrupted?.invoke()
                }

                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)

                            // Audio chunk
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val data = inlineData.optString("data")
                                if (data.isNotEmpty()) {
                                    onAudioChunkReceived?.invoke(data)
                                }
                            }

                            // Text transcript
                            if (part.has("text")) {
                                val text = part.optString("text")
                                if (text.isNotEmpty()) {
                                    onTranscriptReceived?.invoke(text, true)
                                }
                            }
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    onTurnComplete?.invoke()
                }
            }

            // Function calling
            if (root.has("toolCall")) {
                val toolCall = root.getJSONObject("toolCall")
                val functionCalls = toolCall.optJSONArray("functionCalls")
                if (functionCalls != null) {
                    for (i in 0 until functionCalls.length()) {
                        val call = functionCalls.getJSONObject(i)
                        val name = call.optString("name")
                        val id = call.optString("id")
                        val argsObj = call.optJSONObject("args")
                        val argsMap = mutableMapOf<String, Any>()
                        argsObj?.keys()?.forEach { key ->
                            argsMap[key] = argsObj.get(key)
                        }
                        Log.d(TAG, "Received toolCall: $name id: $id args: $argsMap")
                        onToolCall?.invoke(name, argsMap, id)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing server message", e)
        }
    }

    fun disconnect() {
        isConnected = false
        try {
            webSocket?.close(1000, "Client disconnect")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing WebSocket", e)
        } finally {
            webSocket = null
        }
    }
}
