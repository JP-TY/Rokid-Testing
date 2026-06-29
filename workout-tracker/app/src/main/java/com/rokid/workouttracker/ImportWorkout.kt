package com.rokid.workouttracker

import android.content.Context
import android.os.Environment
import java.io.File

object ImportWorkout {
    data class ImportResult(
        val imported: List<String>,
        val errors: List<String>
    )

    fun scanAndImport(context: Context, repository: WorkoutRepository): ImportResult {
        val imported = mutableListOf<String>()
        val errors = mutableListOf<String>()

        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "WorkoutTracker"
        )

        if (!dir.isDirectory) {
            return ImportResult(emptyList(), listOf("Directory not found: ${dir.absolutePath}"))
        }

        val files = dir.listFiles { _, name -> name.endsWith(".json", ignoreCase = true) }
            ?: return ImportResult(emptyList(), listOf("Cannot read directory"))

        for (file in files.sortedBy { it.name }) {
            try {
                val text = file.readText()
                val data = kotlinx.serialization.json.Json
                    .decodeFromString<ExportWorkoutData>(text)
                val template = convertToTemplate(data)
                if (template.exercises.isNotEmpty()) {
                    repository.saveCustomTemplate(template)
                    imported.add(template.name)
                } else {
                    errors.add("${file.name}: no exercises")
                }
            } catch (e: Exception) {
                errors.add("${file.name}: ${e.message ?: "parse error"}")
            }
        }

        return ImportResult(imported, errors)
    }

    private fun convertToTemplate(data: ExportWorkoutData): WorkoutTemplate {
        val exercises = data.exercises.map { ex ->
            val avgReps = if (ex.sets.isNotEmpty()) {
                ex.sets.map { it.reps }.average().toInt().coerceAtLeast(1)
            } else 10
            val avgWeight = if (ex.sets.isNotEmpty()) {
                ex.sets.map { it.weight }.average().toFloat()
            } else 0f
            ExerciseTemplate(
                id = slugify(ex.name),
                name = ex.name.trim(),
                sets = ex.sets.size.coerceAtLeast(1),
                targetReps = avgReps,
                defaultWeight = avgWeight
            )
        }
        return WorkoutTemplate(
            name = data.templateName.trim(),
            exercises = exercises,
            restSeconds = 90
        )
    }
}
