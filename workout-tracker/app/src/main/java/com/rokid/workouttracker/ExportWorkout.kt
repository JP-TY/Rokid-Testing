package com.rokid.workouttracker

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class ExportWorkoutData(
    val templateName: String,
    val date: String,
    val durationSeconds: Long,
    val exercises: List<ExportExercise>
)

@Serializable
data class ExportExercise(
    val name: String,
    val sets: List<ExportSet>
)

@Serializable
data class ExportSet(
    val setNumber: Int,
    val reps: Int,
    val weight: Float,
    val weightUnit: String
)

data class WorkoutResult(
    val templateName: String,
    val totalVolume: Float,
    val weightUnit: WeightUnit,
    val totalSets: Int,
    val durationSeconds: Long
)

object ExportWorkout {
    private val json = Json { prettyPrint = true }

    fun exportSession(
        context: Context,
        session: WorkoutSession,
        durationSeconds: Long
    ): Boolean {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
            val fileName = "${session.template.name}_${dateFormat.format(Date())}.json"
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(session.workoutStartTime))

            val exportData = ExportWorkoutData(
                templateName = session.template.name,
                date = dateStr,
                durationSeconds = durationSeconds,
                exercises = session.exercises.map { ep ->
                    ExportExercise(
                        name = ep.template.name,
                        sets = ep.sets.mapIndexed { i, s ->
                            ExportSet(
                                setNumber = i + 1,
                                reps = s.reps,
                                weight = s.weight,
                                weightUnit = s.weightUnit.name
                            )
                        }
                    )
                }
            )

            val content = json.encodeToString(exportData)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return false
                resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                    ?: return false
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                File(dir, fileName).writeText(content)
            }
            return true
        } catch (_: Exception) {
            return false
        }
    }
}
