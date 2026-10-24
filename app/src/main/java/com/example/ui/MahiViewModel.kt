package com.example.ui

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.audio.AudioPlayer
import com.example.audio.AudioRecorder
import com.example.data.gemini.GeminiLiveSession
import com.example.data.gemini.GeminiRestService
import com.example.data.gemini.MahiPersonality
import com.example.data.tools.ToolExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

sealed interface SessionState {
    data object Disconnected : SessionState
    data object Connecting : SessionState
    data object Listening : SessionState
    data object Thinking : SessionState
    data object Speaking : SessionState
    data class Error(val message: String) : SessionState
}

data class TranscriptEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val speaker: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isUser: Boolean = false,
    val sources: List<String> = emptyList()
)

data class MahiUiState(
    val sessionState: SessionState = SessionState.Disconnected,
    val inputAmplitude: Float = 0f,
    val outputAmplitude: Float = 0f,
    val currentSpeechText: String = "",
    val activeToolBadge: String? = null,
    val selectedVoice: String = "Aoede",
    val selectedLanguage: com.example.data.gemini.SupportedLanguage = com.example.data.gemini.MahiPersonality.AVAILABLE_LANGUAGES[0],
    val sassLevel: Int = 2,
    val isMuted: Boolean = false,
    val transcripts: List<TranscriptEntry> = emptyList(),
    val statusText: String = "Mahi is resting. Tap mic to wake.",
    val hasApiKey: Boolean = false
)

class MahiViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MahiViewModel"
    }

    private val context = application.applicationContext
    private val audioRecorder = AudioRecorder()
    private val audioPlayer = AudioPlayer(context)
    private val toolExecutor = ToolExecutor(context)

    private val apiKey: String = BuildConfig.GEMINI_API_KEY.let {
        if (it.isBlank() || it == "MY_GEMINI_API_KEY") "" else it
    }

    private val geminiRestService = GeminiRestService(apiKey)
    private var liveSession: GeminiLiveSession? = null

    private val _uiState = MutableStateFlow(MahiUiState(hasApiKey = apiKey.isNotEmpty()))
    val uiState: StateFlow<MahiUiState> = _uiState.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var badgeJob: Job? = null
    private var isLiveSessionActive = false

    init {
        // Observe amplitudes for UI orb/waves
        viewModelScope.launch {
            audioRecorder.amplitude.collect { amp ->
                if (_uiState.value.sessionState == SessionState.Listening) {
                    _uiState.update { it.copy(inputAmplitude = amp) }
                }
            }
        }

        viewModelScope.launch {
            audioPlayer.outputAmplitude.collect { amp ->
                if (_uiState.value.sessionState == SessionState.Speaking) {
                    _uiState.update { it.copy(outputAmplitude = amp) }
                }
            }
        }

        audioPlayer.onPlaybackFinished = {
            if (isLiveSessionActive) {
                _uiState.update {
                    it.copy(
                        sessionState = SessionState.Listening,
                        outputAmplitude = 0f,
                        statusText = "Mahi is listening..."
                    )
                }
                startSpeechRecognizer()
            } else {
                _uiState.update {
                    it.copy(
                        sessionState = SessionState.Disconnected,
                        outputAmplitude = 0f,
                        statusText = "Tap mic to talk to Mahi"
                    )
                }
            }
        }
    }

    fun toggleSession(hasPermission: Boolean) {
        if (!hasPermission) {
            _uiState.update {
                it.copy(sessionState = SessionState.Error("Microphone permission required to talk with Mahi"))
            }
            return
        }

        if (isLiveSessionActive) {
            disconnectSession()
        } else {
            startSession()
        }
    }

    fun startSession() {
        audioPlayer.interrupt()
        isLiveSessionActive = true
        _uiState.update {
            it.copy(
                sessionState = SessionState.Connecting,
                statusText = "Connecting to Mahi neural audio stream..."
            )
        }

        val greeting = _uiState.value.selectedLanguage.greeting

        if (apiKey.isEmpty()) {
            // Local high-speed simulation & Android TTS
            viewModelScope.launch {
                delay(600)
                _uiState.update {
                    it.copy(
                        sessionState = SessionState.Speaking,
                        statusText = "Mahi is speaking...",
                        transcripts = it.transcripts + TranscriptEntry(speaker = "Mahi", text = greeting)
                    )
                }
                audioPlayer.playFallbackTts(greeting)
            }
            return
        }

        // Initialize Gemini Live WebSocket Session
        val session = GeminiLiveSession(
            apiKey = apiKey,
            model = "models/gemini-2.5-flash-native-audio-preview-12-2025"
        )
        liveSession = session

        session.onConnected = {
            viewModelScope.launch(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        sessionState = SessionState.Listening,
                        statusText = "Mahi is listening... say anything!",
                        transcripts = it.transcripts + TranscriptEntry(speaker = "Mahi", text = greeting)
                    )
                }
                startStreamingMic()
                startSpeechRecognizer()
            }
        }

        session.onAudioChunkReceived = { base64Data ->
            viewModelScope.launch(Dispatchers.Main) {
                if (_uiState.value.sessionState != SessionState.Speaking) {
                    _uiState.update {
                        it.copy(
                            sessionState = SessionState.Speaking,
                            statusText = "Mahi is speaking..."
                        )
                    }
                }
                audioPlayer.playBase64Chunk(base64Data)
            }
        }

        session.onTranscriptReceived = { text, isModel ->
            viewModelScope.launch(Dispatchers.Main) {
                if (isModel) {
                    val currentList = _uiState.value.transcripts.toMutableList()
                    val last = currentList.lastOrNull()
                    if (last != null && !last.isUser) {
                        currentList[currentList.size - 1] = last.copy(text = last.text + " " + text)
                    } else {
                        currentList.add(TranscriptEntry(speaker = "Mahi", text = text, isUser = false))
                    }
                    _uiState.update { it.copy(transcripts = currentList) }
                }
            }
        }

        session.onInterrupted = {
            viewModelScope.launch(Dispatchers.Main) {
                audioPlayer.interrupt()
                _uiState.update {
                    it.copy(
                        sessionState = SessionState.Listening,
                        statusText = "Listening (you interrupted Mahi)"
                    )
                }
            }
        }

        session.onToolCall = { name, args, callId ->
            viewModelScope.launch(Dispatchers.Main) {
                handleToolCall(name, args, callId)
            }
        }

        session.onError = { error ->
            viewModelScope.launch(Dispatchers.Main) {
                Log.w(TAG, "LiveSession error: $error, falling back to hybrid voice")
                // Graceful fallback to hybrid voice mode
                _uiState.update {
                    it.copy(
                        sessionState = SessionState.Listening,
                        statusText = "Mahi is listening..."
                    )
                }
                startSpeechRecognizer()
            }
        }

        session.connect(
            voiceName = _uiState.value.selectedVoice,
            sassLevel = _uiState.value.sassLevel,
            languageId = _uiState.value.selectedLanguage.id
        )
    }

    private fun startStreamingMic() {
        audioRecorder.onAudioChunk = { _, base64Chunk ->
            liveSession?.sendRealtimeAudio(base64Chunk)
        }
        audioRecorder.startRecording()
    }

    private fun handleToolCall(name: String, args: Map<String, Any>, callId: String) {
        when (name) {
            "openWebsite" -> {
                val url = args["url"]?.toString() ?: "https://google.com"
                val result = toolExecutor.openWebsite(url)
                showToolBadge(result.clientBadge)
                liveSession?.sendToolResponse(callId, result.message)
            }
            "googleSearch" -> {
                val query = args["query"]?.toString() ?: ""
                val result = toolExecutor.openSearch(query)
                showToolBadge(result.clientBadge)
                liveSession?.sendToolResponse(callId, result.message)
            }
            else -> {
                liveSession?.sendToolResponse(callId, "Tool $name not recognized.")
            }
        }
    }

    private fun showToolBadge(badgeText: String) {
        badgeJob?.cancel()
        _uiState.update { it.copy(activeToolBadge = badgeText) }
        badgeJob = viewModelScope.launch {
            delay(3500)
            _uiState.update { it.copy(activeToolBadge = null) }
        }
    }

    fun interruptCurrentSpeech() {
        audioPlayer.interrupt()
        if (isLiveSessionActive) {
            _uiState.update {
                it.copy(
                    sessionState = SessionState.Listening,
                    statusText = "Mahi paused. Go ahead!"
                )
            }
            startSpeechRecognizer()
        }
    }

    private fun startSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return

        viewModelScope.launch(Dispatchers.Main) {
            speechRecognizer?.destroy()
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {}
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {
                            if (_uiState.value.sessionState == SessionState.Listening) {
                                val normalized = ((rmsdB + 2f) / 10f).coerceIn(0f, 1f)
                                _uiState.update { it.copy(inputAmplitude = maxOf(it.inputAmplitude, normalized)) }
                            }
                        }

                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        override fun onError(error: Int) {
                            // Retry speech recognizer if session is still active
                            if (isLiveSessionActive && _uiState.value.sessionState == SessionState.Listening) {
                                viewModelScope.launch {
                                    delay(500)
                                    startSpeechRecognizer()
                                }
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val userText = matches?.firstOrNull() ?: ""
                            if (userText.isNotBlank()) {
                                handleUserUtterance(userText)
                            } else if (isLiveSessionActive) {
                                startSpeechRecognizer()
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val partial = matches?.firstOrNull() ?: ""
                            if (partial.isNotBlank()) {
                                _uiState.update { it.copy(currentSpeechText = partial) }
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    val localeTag = _uiState.value.selectedLanguage.localeTag
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognizer", e)
            }
        }
    }

    /**
     * Handles detected user speech either through Gemini Rest (Search Grounding & TTS) or live stream.
     */
    fun handleUserUtterance(userText: String) {
        _uiState.update {
            it.copy(
                sessionState = SessionState.Thinking,
                currentSpeechText = "",
                statusText = "Thinking up a witty response...",
                transcripts = it.transcripts + TranscriptEntry(
                    speaker = "You",
                    text = userText,
                    isUser = true
                )
            )
        }

        // Check for local tool intent trigger (e.g., "open YouTube", "open Google")
        val lower = userText.lowercase()
        if (lower.contains("open youtube")) {
            val result = toolExecutor.openWebsite("https://youtube.com")
            showToolBadge(result.clientBadge)
        } else if (lower.contains("open google")) {
            val result = toolExecutor.openWebsite("https://google.com")
            showToolBadge(result.clientBadge)
        } else if (lower.contains("open twitter") || lower.contains("open x")) {
            val result = toolExecutor.openWebsite("https://x.com")
            showToolBadge(result.clientBadge)
        }

        viewModelScope.launch {
            if (apiKey.isNotEmpty()) {
                // Grounded generation using gemini-3.8-flash with googleSearch tool
                val grounded = geminiRestService.generateGroundedResponse(
                    prompt = userText,
                    sassLevel = _uiState.value.sassLevel,
                    languageId = _uiState.value.selectedLanguage.id
                )
                val replyText = grounded.text

                _uiState.update {
                    it.copy(
                        sessionState = SessionState.Speaking,
                        statusText = "Mahi is speaking...",
                        transcripts = it.transcripts + TranscriptEntry(
                            speaker = "Mahi",
                            text = replyText,
                            sources = grounded.searchSources
                        )
                    )
                }

                // Convert text to speech using gemini-3.8-flash-tts
                val audioData = geminiRestService.generateSpeechAudio(replyText, _uiState.value.selectedVoice)
                if (audioData != null) {
                    audioPlayer.playBase64Chunk(audioData)
                } else {
                    audioPlayer.playFallbackTts(replyText)
                }
            } else {
                // Offline / unconfigured sample reply with sass
                val offlineReplies = when (_uiState.value.selectedLanguage.id) {
                    "hinglish" -> listOf(
                        "Arey yaar, tumhara confidence mast hai, par ek API key daal do taaki mera full brain chale!",
                        "Sahi bol rahe ho janab! Par thoda aur wild sawal pucho na mujhse.",
                        "Waah! Tumhare saath baat karne mein toh alag hi maza hai. Aur sunao!"
                    )
                    "hi" -> listOf(
                        "अरे वाह! आपकी बात में दम तो है, पर कृपया एपीआई कुंजी डालिए ताकि मेरा दिमाग पूरी तरह चले!",
                        "मज़ेदार बात कही आपने! कुछ और दिलचस्प पूछिए ना।",
                        "बातें तो बहुत खूब करते हैं आप! आगे बताइए क्या हाल है?"
                    )
                    else -> listOf(
                        "You know I love your confidence, but give me an API key so my full brain works!",
                        "That's cute! I totally agree, though you should definitely ask me something wilder.",
                        "Darling, you always know how to make conversation interesting. Keep talking!"
                    )
                }
                val reply = offlineReplies.random()
                _uiState.update {
                    it.copy(
                        sessionState = SessionState.Speaking,
                        statusText = "Mahi is speaking...",
                        transcripts = it.transcripts + TranscriptEntry(
                            speaker = "Mahi",
                            text = reply
                        )
                    )
                }
                audioPlayer.playFallbackTts(reply)
            }
        }
    }

    fun updateLanguage(newLanguage: com.example.data.gemini.SupportedLanguage) {
        _uiState.update {
            it.copy(
                selectedLanguage = newLanguage,
                statusText = "Language changed to ${newLanguage.name}"
            )
        }
        audioPlayer.setLanguage(newLanguage.localeTag)

        val switchGreeting = when (newLanguage.id) {
            "hinglish" -> "Arey waah! Ab hum Hinglish mein baat karenge. Haan ji bolo, kya chal raha hai?"
            "hi" -> "बहुत बढ़िया! अब हम हिन्दी में बात करेंगे। बताइए क्या सेवा करें?"
            "es" -> "¡Excelente! Ahora estamos hablando en español. ¿De qué quieres hablar?"
            "fr" -> "Parfait! Nous parlons maintenant français. Qu'est-ce que tu veux savoir?"
            else -> "Awesome! Switched to ${newLanguage.name}. What's on your mind?"
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    sessionState = SessionState.Speaking,
                    statusText = "Mahi speaking in ${newLanguage.name}...",
                    transcripts = it.transcripts + TranscriptEntry(
                        speaker = "Mahi",
                        text = switchGreeting
                    )
                )
            }
            if (apiKey.isNotEmpty()) {
                val audio = geminiRestService.generateSpeechAudio(switchGreeting, _uiState.value.selectedVoice)
                if (audio != null) {
                    audioPlayer.playBase64Chunk(audio)
                    return@launch
                }
            }
            audioPlayer.playFallbackTts(switchGreeting)
        }

        if (isLiveSessionActive && liveSession != null) {
            liveSession?.disconnect()
            startSession()
        }
    }

    fun sendQuickPrompt(promptText: String) {
        if (!isLiveSessionActive) {
            isLiveSessionActive = true
            startStreamingMic()
        }
        handleUserUtterance(promptText)
    }

    fun updateSassLevel(level: Int) {
        _uiState.update { it.copy(sassLevel = level) }
    }

    fun updateVoice(voiceName: String) {
        _uiState.update { it.copy(selectedVoice = voiceName) }
    }

    fun testVoiceSample() {
        val sample = when (_uiState.value.sassLevel) {
            1 -> "Hello there! I'm Mahi, your lovely assistant."
            3 -> "Honey, if attitude was currency, I'd own the whole metaverse. What do you need?"
            else -> "Hey! I'm Mahi. Ready for some smart answers and a little sass?"
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    sessionState = SessionState.Speaking,
                    statusText = "Testing voice..."
                )
            }
            if (apiKey.isNotEmpty()) {
                val audio = geminiRestService.generateSpeechAudio(sample, _uiState.value.selectedVoice)
                if (audio != null) {
                    audioPlayer.playBase64Chunk(audio)
                    return@launch
                }
            }
            audioPlayer.playFallbackTts(sample)
        }
    }

    fun disconnectSession() {
        isLiveSessionActive = false
        audioRecorder.stopRecording()
        audioPlayer.interrupt()
        liveSession?.disconnect()
        liveSession = null

        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up speech recognizer", e)
        }

        _uiState.update {
            it.copy(
                sessionState = SessionState.Disconnected,
                inputAmplitude = 0f,
                outputAmplitude = 0f,
                currentSpeechText = "",
                statusText = "Mahi disconnected. Tap mic to wake."
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnectSession()
        audioPlayer.release()
    }
}
