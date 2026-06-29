package com.rokid.workouttracker

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

object ImportWorkout {
    data class ImportResult(
        val imported: List<String>,
        val errors: List<String>
    )

    fun scanAndImport(context: Context, repository: WorkoutRepository): ImportResult {
        val imported = mutableListOf<String>()
        val errors = mutableListOf<String>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME
        )
        val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("Download/WorkoutTracker%", "%.json")

        val cursor: Cursor? = context.contentResolver.query(
            collection, projection, selection, selectionArgs,
            "${MediaStore.Downloads.DISPLAY_NAME} ASC"
        )

        if (cursor == null) {
            return ImportResult(emptyList(), listOf("Cannot query downloads"))
        }

        cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val name = c.getString(nameCol)
                val uri: Uri = Uri.withAppendedPath(collection, id.toString())
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    errors.add("$name: could not open file")
                    continue
                }
                try {
                    val text = inputStream.use { it.bufferedReader().readText() }
                    val data = kotlinx.serialization.json.Json
                        .decodeFromString<ExportWorkoutData>(text)
                    val template = convertToTemplate(data)
                    if (template.exercises.isNotEmpty()) {
                        repository.saveCustomTemplate(template)
                        imported.add(template.name)
                    } else {
                        errors.add("$name: no exercises")
                    }
                } catch (e: Exception) {
                    errors.add("$name: ${e.message ?: "parse error"}")
                }
            }
        }

        if (imported.isEmpty() && errors.isEmpty()) {
            return ImportResult(emptyList(), listOf("No files found"))
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
