package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.sqrt

/**
 * AudioPlayer handles continuous low-latency 24kHz/16kHz PCM16 audio playback via AudioTrack.
 * It also provides on-device TextToSpeech fallback and real-time playback amplitude analysis.
 */
class AudioPlayer(context: Context) {

    companion object {
        const val LIVE_SAMPLE_RATE = 24000 // Gemini Live standard output
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val TAG = "AudioPlayer"
    }

    private var audioTrack: AudioTrack? = null
    private val audioQueue = ConcurrentLinkedQueue<ByteArray>()
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _outputAmplitude = MutableStateFlow(0f)
    val outputAmplitude: StateFlow<Float> = _outputAmplitude.asStateFlow()

    // Fallback Android TextToSpeech
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false

    var onPlaybackFinished: (() -> Unit)? = null

    init {
        try {
            textToSpeech = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech?.language = Locale.US
                    textToSpeech?.setSpeechRate(1.05f) // Sassy, lively pace
                    textToSpeech?.setPitch(1.15f) // Bright, youthful female pitch
                    ttsReady = true
                }
            }

            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isPlaying.value = true
                    _outputAmplitude.value = 0.7f
                }

                override fun onDone(utteranceId: String?) {
                    _isPlaying.value = false
                    _outputAmplitude.value = 0f
                    onPlaybackFinished?.invoke()
                }

                override fun onError(utteranceId: String?) {
                    _isPlaying.value = false
                    _outputAmplitude.value = 0f
                    onPlaybackFinished?.invoke()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Android TTS", e)
        }
    }

    fun setLanguage(localeTag: String) {
        try {
            val locale = Locale.forLanguageTag(localeTag)
            textToSpeech?.language = locale
        } catch (e: Exception) {
            Log.e(TAG, "Error setting language on TTS", e)
        }
    }

    @Synchronized
    private fun ensureAudioTrack(sampleRate: Int = LIVE_SAMPLE_RATE) {
        if (audioTrack != null && audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
            return
        }

        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = maxOf(minBufferSize * 4, 8192)

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AUDIO_FORMAT)
            .setSampleRate(sampleRate)
            .setChannelMask(CHANNEL_CONFIG)
            .build()

        audioTrack = AudioTrack(
            attributes,
            format,
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        try {
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioTrack", e)
        }
    }

    fun playBase64Chunk(base64Data: String) {
        try {
            val pcmBytes = Base64.decode(base64Data, Base64.DEFAULT)
            enqueuePcmBytes(pcmBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding base64 audio", e)
        }
    }

    fun enqueuePcmBytes(pcmBytes: ByteArray) {
        audioQueue.add(pcmBytes)
        startPlaybackLoopIfNeeded()
    }

    private fun startPlaybackLoopIfNeeded() {
        if (playbackJob?.isActive == true) return

        playbackJob = scope.launch {
            ensureAudioTrack()
            _isPlaying.value = true

            while (isActive) {
                val chunk = audioQueue.poll()
                if (chunk != null) {
                    // Compute amplitude for UI visualization while speaking
                    var sum = 0.0
                    var i = 0
                    while (i < chunk.size - 1) {
                        val sample = (chunk[i].toInt() and 0xFF) or (chunk[i + 1].toInt() shl 8)
                        val shortSample = sample.toShort()
                        sum += shortSample * shortSample
                        i += 2
                    }
                    val sampleCount = chunk.size / 2
                    val rms = if (sampleCount > 0) sqrt(sum / sampleCount) else 0.0
                    val normalized = (rms / 32768.0).toFloat().coerceIn(0f, 1f) * 3.5f
                    _outputAmplitude.value = normalized.coerceIn(0.1f, 1.0f)

                    audioTrack?.write(chunk, 0, chunk.size)
                } else {
                    _outputAmplitude.value = 0f
                    // Wait briefly for more audio chunks
                    var waitedMs = 0
                    var hasNewChunk = false
                    while (waitedMs < 250) {
                        kotlinx.coroutines.delay(20)
                        waitedMs += 20
                        if (!audioQueue.isEmpty()) {
                            hasNewChunk = true
                            break
                        }
                    }
                    if (!hasNewChunk) {
                        break
                    }
                }
            }

            _isPlaying.value = false
            _outputAmplitude.value = 0f
            onPlaybackFinished?.invoke()
        }
    }

    fun playFallbackTts(text: String) {
        interrupt()
        if (ttsReady && textToSpeech != null) {
            val params = android.os.Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "mahi_reply")
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "mahi_reply")
        }
    }

    fun interrupt() {
        audioQueue.clear()
        playbackJob?.cancel()
        playbackJob = null
        _isPlaying.value = false
        _outputAmplitude.value = 0f

        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error flushing AudioTrack", e)
        }

        try {
            textToSpeech?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS", e)
        }
    }

    fun release() {
        interrupt()
        try {
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack", e)
        }

        try {
            textToSpeech?.shutdown()
            textToSpeech = null
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
    }
}
