package com.rokid.workouttracker

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

internal class VoiceCommandRecognizer(
    private val context: Context,
    private val grammarCommands: Set<String>,
    private val onResult: (VoiceCommandResult) -> Unit
) {
    companion object {
        private const val TAG = "VoiceCommand"
        private const val SAMPLE_RATE_HZ = 16_000
        private const val DUPLICATE_WINDOW_MS = 400L
        private const val AUDIO_LEVEL_INTERVAL_MS = 150L
        private const val MIN_BUFFER_DURATION_MS = 200
        private const val AUDIO_READ_CHUNK_MS = 50
        private const val MIC_GAIN = 50.0f

        fun buildGrammarJson(commands: Set<String>): String {
            val normalized = commands.map { it.trim().lowercase(Locale.US) }.toSet()
            return JSONArray().apply {
                normalized.forEach { put(it) }
                put("[unk]")
            }.toString()
        }

        val NAME_WORDS = setOf(
            "upper", "lower", "body", "push", "pull", "day", "split", "full",
            "leg", "chest", "back", "shoulder", "arm", "core", "abs", "beginner",
            "advanced", "light", "heavy", "cardio", "strength", "hypertrophy",
            "power", "endurance", "hiit", "circuit", "superset", "morning",
            "evening", "warmup", "cooldown", "stretch", "bench", "press", "squat",
            "deadlift", "row", "curl", "extension", "raise", "fly", "pulldown",
            "crunch", "plank", "pushup", "pullup", "dip", "lunge", "tricep",
            "bicep", "dumbbell", "barbell", "kettlebell", "cable", "machine",
            "smith", "incline", "decline", "flat", "overhead", "lateral", "front",
            "rear", "seated", "standing", "reverse", "hammer", "concentration",
            "preacher", "crusher", "kickback", "shrug", "upright", "clean",
            "jerk", "snatch", "thruster", "a", "b", "c", "d", "e", "f", "g", "h",
            "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u",
            "v", "w", "x", "y", "z", "space", "done", "confirm", "delete",
            "backspace", "finish"
        )
    }

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var workerThread: Thread? = null
    private val stopRequested = AtomicBoolean(false)
    private var lastCommandTime = 0L
    private var lastAudioLevelTime = 0L

    private var grammarJson: String = buildGrammarJson(grammarCommands)
    private var nameGrammarJson: String = buildGrammarJson(NAME_WORDS)
    private var dictationMode = false
    private var nameMode = false

    private var isModelReady = false

    val hasRecordPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    val modelAvailable: Boolean
        get() {
            val assets = context.assets
            return try {
                assets.list("model-en-us")?.isNotEmpty() == true
            } catch (_: Exception) {
                false
            }
        }

    fun loadModel() {
        if (isModelReady) return
        try {
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            org.vosk.android.StorageService.unpack(context.applicationContext, "model-en-us", "model",
                { loadedModel ->
                    model = loadedModel
                    isModelReady = true
                    onResult(VoiceCommandResult.ModelReady)
                },
                { exception ->
                    Log.e(TAG, "Model unpack failed", exception)
                    onResult(VoiceCommandResult.ModelFailed)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Model load failed", e)
            onResult(VoiceCommandResult.ModelFailed)
        }
    }

    @SuppressLint("MissingPermission")
    fun startListening(dictation: Boolean = false, nameGrammar: Boolean = false) {
        if (!isModelReady || !hasRecordPermission) return
        if (workerThread != null) stopListening()

        dictationMode = dictation
        nameMode = nameGrammar
        stopRequested.set(false)

        val m = model ?: return

        try {
            recognizer = if (dictationMode) {
                Recognizer(m, SAMPLE_RATE_HZ.toFloat()).apply {
                    setWords(true)
                    setPartialWords(true)
                }
            } else if (nameMode) {
                Recognizer(m, SAMPLE_RATE_HZ.toFloat(), nameGrammarJson).apply {
                    setWords(true)
                    setPartialWords(true)
                }
            } else {
                Recognizer(m, SAMPLE_RATE_HZ.toFloat(), grammarJson).apply {
                    setWords(true)
                    setPartialWords(true)
                }
            }
        } catch (e: IOException) {
            onResult(VoiceCommandResult.Error("Recognizer init failed: ${e.message}"))
            return
        }

        val minBufferBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferBytes <= 0) {
            onResult(VoiceCommandResult.Error("Invalid buffer size: $minBufferBytes"))
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBufferBytes, SAMPLE_RATE_HZ * MIN_BUFFER_DURATION_MS / 1000 * 2)
        )

        val record = audioRecord
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            onResult(VoiceCommandResult.Error("AudioRecord init failed"))
            return
        }

        try {
            record.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "startRecording failed", e)
            onResult(VoiceCommandResult.Error("Start recording failed"))
            record.release()
            audioRecord = null
            return
        }
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            onResult(VoiceCommandResult.Error("Not recording"))
            record.release()
            audioRecord = null
            return
        }

        recognizer!!.reset()
        lastCommandTime = 0L
        lastAudioLevelTime = 0L
        onResult(VoiceCommandResult.ListeningStarted)

        workerThread = Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val buffer = ShortArray(SAMPLE_RATE_HZ * AUDIO_READ_CHUNK_MS / 1000)
            val r = recognizer ?: return@Thread
            val rec = audioRecord ?: return@Thread

            while (!stopRequested.get()) {
                val readCount = rec.read(buffer, 0, buffer.size)
                if (stopRequested.get()) break
                if (readCount < 0) {
                    Log.w(TAG, "Audio read error: $readCount")
                    break
                }
                if (readCount == 0) continue

                // Apply gain boost for low-sensitivity Rokid mic
                for (i in 0 until readCount) {
                    val v = (buffer[i].toFloat() * MIC_GAIN).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    buffer[i] = v.toShort()
                }

                val now = System.currentTimeMillis()
                if (now - lastAudioLevelTime >= AUDIO_LEVEL_INTERVAL_MS) {
                    lastAudioLevelTime = now
                    val level = computeAudioLevel(buffer, readCount)
                    onResult(VoiceCommandResult.AudioLevel(level))
                }

                val accepted = r.acceptWaveForm(buffer, readCount)
                if (accepted) {
                    val resultJson = r.result
                    if (dictationMode) {
                        val text = parseDictationText(resultJson)
                        if (text.isNotEmpty()) {
                            onResult(VoiceCommandResult.Dictation(text, true))
                        }
                    } else if (nameMode) {
                        val cmd = parseCommandText(resultJson)
                        if (cmd.isNotEmpty()) {
                            onResult(VoiceCommandResult.Dictation(cmd, false))
                        }
                    } else {
                        val cmd = parseCommandText(resultJson)
                        dispatchCommand(cmd)
                    }
                } else if (dictationMode) {
                    val partial = parseDictationText(r.getPartialResult())
                    if (partial.isNotEmpty()) {
                        onResult(VoiceCommandResult.Dictation(partial, false))
                    }
                }
            }

            if (!stopRequested.get()) {
                val final = r.finalResult
                if (dictationMode) {
                    val text = parseDictationText(final)
                    if (text.isNotEmpty()) {
                        onResult(VoiceCommandResult.Dictation(text, true))
                    }
                }
            }
        }.apply {
            name = "voice-recognizer"
            start()
        }
    }

    fun stopListening() {
        stopRequested.set(true)
        try {
            audioRecord?.stop()
        } catch (_: Exception) {}
        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        workerThread?.join(1000)
        workerThread = null
        onResult(VoiceCommandResult.ListeningStopped)
    }

    fun destroy() {
        stopListening()
        recognizer?.close()
        recognizer = null
        model?.close()
        model = null
        isModelReady = false
    }

    fun switchToDictation() {
        if (workerThread != null) {
            stopListening()
            startListening(dictation = true)
        } else {
            dictationMode = true
        }
    }

    fun switchToNameGrammar() {
        if (workerThread != null) {
            stopListening()
            nameMode = true
            startListening(nameGrammar = true)
        } else {
            nameMode = true
        }
    }

    fun switchToCommandGrammar() {
        if (workerThread != null) {
            stopListening()
            nameMode = false
            startListening(nameGrammar = false)
        } else {
            nameMode = false
        }
    }

    fun switchToGrammar() {
        if (workerThread != null) {
            stopListening()
            nameMode = false
            startListening(dictation = false)
        } else {
            dictationMode = false
            nameMode = false
        }
    }

    private fun dispatchCommand(text: String) {
        if (text.isEmpty()) return
        val now = System.currentTimeMillis()
        if (now - lastCommandTime < DUPLICATE_WINDOW_MS) return
        lastCommandTime = now
        onResult(VoiceCommandResult.Recognized(text))
    }

    private fun parseCommandText(resultJson: String): String {
        return JSONObject(resultJson)
            .optString("text", "")
            .trim()
            .lowercase(Locale.US)
    }

    private fun parseDictationText(resultJson: String): String {
        return JSONObject(resultJson)
            .optString("text", "")
            .trim()
    }

    private fun computeAudioLevel(buffer: ShortArray, count: Int): Float {
        if (count == 0) return 0f
        var sum = 0.0
        for (i in 0 until count) {
            sum += abs(buffer[i].toDouble())
        }
        return (sum / count / 32768.0).toFloat().coerceIn(0f, 1f)
    }

    private fun abs(v: Double): Double = if (v < 0) -v else v
}

