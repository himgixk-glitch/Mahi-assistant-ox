package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
import kotlin.math.sqrt

/**
 * AudioRecorder captures 16kHz 16-bit Mono PCM audio for Gemini Live API streaming.
 * It also computes real-time RMS amplitude for responsive UI visualizers.
 */
class AudioRecorder {

    companion object {
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val TAG = "AudioRecorder"
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    var onAudioChunk: ((ByteArray, String) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun startRecording(chunkDurationMs: Int = 100): Boolean {
        if (_isRecording.value) return true

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid AudioRecord buffer size")
            return false
        }

        // Buffer for requested duration (100ms chunk = 1600 samples = 3200 bytes)
        val chunkByteSize = (SAMPLE_RATE * 2 * (chunkDurationMs / 1000f)).toInt()
        val bufferSize = maxOf(minBufferSize * 2, chunkByteSize)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                // Fallback to default MIC source if VOICE_RECOGNITION fails
                audioRecord?.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            _isRecording.value = true

            recordingJob = scope.launch {
                val readBuffer = ByteArray(chunkByteSize)
                while (isActive && _isRecording.value) {
                    val readBytes = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: -1
                    if (readBytes > 0) {
                        // Calculate RMS amplitude for UI audio visualizer
                        var sum = 0.0
                        var i = 0
                        while (i < readBytes - 1) {
                            val sample = (readBuffer[i].toInt() and 0xFF) or (readBuffer[i + 1].toInt() shl 8)
                            val shortSample = sample.toShort()
                            sum += shortSample * shortSample
                            i += 2
                        }
                        val sampleCount = readBytes / 2
                        val rms = if (sampleCount > 0) sqrt(sum / sampleCount) else 0.0
                        // Normalize RMS roughly between 0.0 and 1.0 with smoothing
                        val normalized = (rms / 32768.0).toFloat().coerceIn(0f, 1f) * 3f
                        _amplitude.value = normalized.coerceIn(0f, 1f)

                        // Send copy of audio chunk and base64 encoded payload
                        val chunkCopy = readBuffer.copyOf(readBytes)
                        val base64Data = Base64.encodeToString(chunkCopy, Base64.NO_WRAP)
                        onAudioChunk?.invoke(chunkCopy, base64Data)
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioRecord", e)
            stopRecording()
            return false
        }
    }

    fun stopRecording() {
        _isRecording.value = false
        _amplitude.value = 0f
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord", e)
        } finally {
            audioRecord = null
        }
    }
}
