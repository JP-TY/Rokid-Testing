package com.rokid.workouttracker

internal sealed interface VoiceCommandResult {
    data class Recognized(val command: String) : VoiceCommandResult
    data class Dictation(val text: String, val isFinal: Boolean) : VoiceCommandResult
    data class AudioLevel(val level: Float) : VoiceCommandResult
    data class Error(val message: String) : VoiceCommandResult
    object ModelReady : VoiceCommandResult
    object ModelFailed : VoiceCommandResult
    object ListeningStarted : VoiceCommandResult
    object ListeningStopped : VoiceCommandResult
}
